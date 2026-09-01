package io.toolbox.kernel

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks registry mutations owned by one module for its full loaded lifetime.
 *
 * Initial load/start runs as a transaction from baseline 0. After commit, ownership
 * actions are retained so uninstall/unload can deterministically restore the pre-module
 * registry state. A restart transaction uses a checkpoint so a failed restart rolls back
 * only mutations made since that restart began while preserving the previously committed
 * module ownership baseline.
 */
internal class KernelRegistryMutationJournal {
    internal data class Mutation<T>(
        val result: T,
        val undo: (() -> Unit)? = null
    )

    private enum class State {
        OPEN,
        COMMITTED,
        ROLLING_BACK,
        RELEASED
    }

    private val lock = Any()
    private val undoActions = ArrayDeque<() -> Unit>()
    private var state = State.OPEN
    private var rollbackTargetSize = 0

    fun <T> mutate(block: () -> Mutation<T>): T = synchronized(lock) {
        when (state) {
            State.OPEN,
            State.COMMITTED -> {
                val mutation = block()
                mutation.undo?.let(undoActions::addFirst)
                mutation.result
            }

            State.ROLLING_BACK,
            State.RELEASED -> error("Registry mutation attempted through an inactive module context")
        }
    }

    fun begin() = synchronized(lock) {
        check(state == State.COMMITTED) { "Registry mutation journal cannot begin from $state" }
        rollbackTargetSize = undoActions.size
        state = State.OPEN
    }

    fun commit() = synchronized(lock) {
        check(state == State.OPEN) { "Registry mutation journal cannot commit from $state" }
        state = State.COMMITTED
    }

    fun rollbackIfOpen(): List<Throwable> = synchronized(lock) {
        when (state) {
            State.OPEN -> {
                val target = rollbackTargetSize
                val terminal = if (target == 0) State.RELEASED else State.COMMITTED
                rollbackToLocked(target, terminal, State.OPEN)
            }

            State.COMMITTED,
            State.RELEASED -> emptyList()
            State.ROLLING_BACK -> error("Registry mutation journal is already rolling back")
        }
    }

    fun releaseAll(): List<Throwable> = synchronized(lock) {
        when (state) {
            State.RELEASED -> emptyList()
            State.ROLLING_BACK -> error("Registry mutation journal is already rolling back")
            State.OPEN,
            State.COMMITTED -> {
                val failureState = state
                rollbackToLocked(0, State.RELEASED, failureState)
            }
        }
    }

    private fun rollbackToLocked(
        targetSize: Int,
        terminalState: State,
        failureState: State
    ): List<Throwable> {
        check(targetSize in 0..undoActions.size) { "Invalid registry rollback checkpoint" }
        state = State.ROLLING_BACK
        val failures = mutableListOf<Throwable>()
        while (undoActions.size > targetSize) {
            val action = undoActions.first()
            val result = runCatching { action.invoke() }
            if (result.isFailure) {
                failures += result.exceptionOrNull()!!
                state = failureState
                return failures
            }
            undoActions.removeFirst()
        }
        state = terminalState
        return failures
    }
}

private fun <T> applyRegistryMutation(
    journal: KernelRegistryMutationJournal?,
    block: () -> KernelRegistryMutationJournal.Mutation<T>
): T = journal?.mutate(block) ?: block().result

class ServiceRegistry private constructor(
    private val services: ConcurrentHashMap<Class<*>, Any>,
    private val journal: KernelRegistryMutationJournal?
) {
    constructor() : this(ConcurrentHashMap(), null)

    internal fun transactionalView(journal: KernelRegistryMutationJournal): ServiceRegistry =
        ServiceRegistry(services, journal)

    fun <T : Any> register(type: Class<T>, service: T, replace: Boolean = false) {
        applyRegistryMutation(journal) {
            if (replace) {
                val previous = services.put(type, service)
                KernelRegistryMutationJournal.Mutation(Unit) {
                    services.compute(type) { _, current ->
                        if (current === service) previous else current
                    }
                }
            } else {
                check(services.putIfAbsent(type, service) == null) {
                    "Service already registered: ${type.name}"
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    services.compute(type) { _, current ->
                        if (current === service) null else current
                    }
                }
            }
        }
    }

    fun <T : Any> get(type: Class<T>): T? = services[type]?.let(type::cast)

    fun unregister(type: Class<*>) {
        applyRegistryMutation(journal) {
            val removed = services.remove(type)
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = removed?.let { previous ->
                    { services.putIfAbsent(type, previous); Unit }
                }
            )
        }
    }

    val size: Int get() = services.size
}

class CapabilityRegistry private constructor(
    private val capabilities: ConcurrentHashMap<String, Capability>,
    private val journal: KernelRegistryMutationJournal?,
    private val ownerModuleId: String?
) {
    private data class RegisteredCapability(
        override val id: String,
        override val version: Int,
        override val providerModuleId: String
    ) : Capability

    constructor() : this(ConcurrentHashMap(), null, null)

    internal fun transactionalView(
        journal: KernelRegistryMutationJournal,
        ownerModuleId: String
    ): CapabilityRegistry = CapabilityRegistry(capabilities, journal, ownerModuleId)

    fun register(capability: Capability, replace: Boolean = false) {
        val id = capability.id
        val version = capability.version
        val providerModuleId = capability.providerModuleId

        require(id.isNotBlank()) { "Capability id cannot be blank" }
        require(id.none(Char::isWhitespace)) { "Capability id cannot contain whitespace" }
        require(version > 0) { "Capability version must be positive" }
        require(providerModuleId.isNotBlank()) { "Capability provider module id cannot be blank" }
        require(providerModuleId.none(Char::isWhitespace)) {
            "Capability provider module id cannot contain whitespace"
        }
        ownerModuleId?.let { owner ->
            require(providerModuleId == owner) {
                "Capability provider $providerModuleId does not match owning module $owner"
            }
        }

        val registered = RegisteredCapability(
            id = id,
            version = version,
            providerModuleId = providerModuleId
        )
        applyRegistryMutation(journal) {
            if (replace) {
                val previous = capabilities.put(id, registered)
                KernelRegistryMutationJournal.Mutation(Unit) {
                    capabilities.compute(id) { _, current ->
                        if (current === registered) previous else current
                    }
                }
            } else {
                check(capabilities.putIfAbsent(id, registered) == null) {
                    "Capability already registered: $id"
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    capabilities.compute(id) { _, current ->
                        if (current === registered) null else current
                    }
                }
            }
        }
    }

    fun get(id: String): Capability? = capabilities[id]

    fun unregister(id: String) {
        applyRegistryMutation(journal) {
            val removed = capabilities.remove(id)
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = removed?.let { previous ->
                    { capabilities.putIfAbsent(id, previous); Unit }
                }
            )
        }
    }

    fun all(): List<Capability> = capabilities.values.sortedBy { it.id }

    val size: Int get() = capabilities.size
}

class EventBus private constructor(
    private val logger: KernelLogger,
    private val listeners: ConcurrentHashMap<String, CopyOnWriteArrayList<(KernelEvent) -> Unit>>,
    private val journal: KernelRegistryMutationJournal?
) {
    constructor(logger: KernelLogger = NoopKernelLogger) : this(logger, ConcurrentHashMap(), null)

    internal fun transactionalView(journal: KernelRegistryMutationJournal): EventBus =
        EventBus(logger, listeners, journal)

    fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription {
        require(topic.isNotBlank()) { "Event topic cannot be blank" }
        return applyRegistryMutation(journal) {
            var bucket: CopyOnWriteArrayList<(KernelEvent) -> Unit>? = null
            listeners.compute(topic) { _, current ->
                val selected = current ?: CopyOnWriteArrayList()
                selected += listener
                bucket = selected
                selected
            }
            val selectedBucket = checkNotNull(bucket)
            val subscription = Subscription {
                removeSubscription(topic, selectedBucket, listener)
            }
            KernelRegistryMutationJournal.Mutation(subscription) {
                removeListenerAtomically(topic, selectedBucket, listener)
            }
        }
    }

    private fun removeSubscription(
        topic: String,
        bucket: CopyOnWriteArrayList<(KernelEvent) -> Unit>,
        listener: (KernelEvent) -> Unit
    ) {
        applyRegistryMutation(journal) {
            val removed = bucket.remove(listener)
            if (bucket.isEmpty()) {
                // The first observation may be stale under a racing subscribe. Re-check the
                // mapped bucket atomically before removing the topic entry.
                listeners.computeIfPresent(topic) { _, current ->
                    if (current === bucket && current.size == 0) null else current
                }
            }
            val undo: (() -> Unit)? = if (removed) {
                {
                    listeners.compute(topic) { _, current ->
                        val selected = current ?: bucket
                        if (!selected.contains(listener)) selected.add(listener)
                        selected
                    }
                    Unit
                }
            } else {
                null
            }
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = undo
            )
        }
    }

    private fun removeListenerAtomically(
        topic: String,
        bucket: CopyOnWriteArrayList<(KernelEvent) -> Unit>,
        listener: (KernelEvent) -> Unit
    ) {
        bucket.remove(listener)
        listeners.computeIfPresent(topic) { _, current ->
            if (current === bucket && current.size == 0) null else current
        }
    }

    fun publish(event: KernelEvent) {
        dispatch(event.topic, event)
        if (event.topic != WILDCARD) {
            dispatch(WILDCARD, event)
        }
    }

    private fun dispatch(topic: String, event: KernelEvent) {
        listeners[topic]?.forEach { listener ->
            runCatching { listener(event) }
                .onFailure { error ->
                    runCatching { logger.warn("Event listener failed for topic ${event.topic}", error) }
                }
        }
    }

    fun interface Subscription : AutoCloseable {
        override fun close()
    }

    companion object {
        const val WILDCARD = "*"
    }
}

class CommandBus private constructor(
    private val handlers: ConcurrentHashMap<String, (KernelCommand) -> CommandResult>,
    private val journal: KernelRegistryMutationJournal?
) {
    constructor() : this(ConcurrentHashMap(), null)

    internal fun transactionalView(journal: KernelRegistryMutationJournal): CommandBus =
        CommandBus(handlers, journal)

    fun register(
        commandName: String,
        replace: Boolean = false,
        handler: (KernelCommand) -> CommandResult
    ) {
        require(commandName.isNotBlank()) { "Command name cannot be blank" }
        require(commandName.none(Char::isWhitespace)) { "Command name cannot contain whitespace" }
        applyRegistryMutation(journal) {
            if (replace) {
                val previous = handlers.put(commandName, handler)
                KernelRegistryMutationJournal.Mutation(Unit) {
                    handlers.compute(commandName) { _, current ->
                        if (current === handler) previous else current
                    }
                }
            } else {
                check(handlers.putIfAbsent(commandName, handler) == null) {
                    "Command already registered: $commandName"
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    handlers.compute(commandName) { _, current ->
                        if (current === handler) null else current
                    }
                }
            }
        }
    }

    fun execute(command: KernelCommand): CommandResult {
        if (command.name.isBlank() || command.name.any(Char::isWhitespace)) {
            return CommandResult.failure(IllegalArgumentException("Command name is invalid"))
        }
        val handler = handlers[command.name]
            ?: return CommandResult.failure(IllegalArgumentException("No handler for command: ${command.name}"))
        return runCatching { handler(command) }
            .getOrElse(CommandResult::failure)
    }

    fun unregister(commandName: String) {
        applyRegistryMutation(journal) {
            val removed = handlers.remove(commandName)
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = removed?.let { previous ->
                    { handlers.putIfAbsent(commandName, previous); Unit }
                }
            )
        }
    }

    val size: Int get() = handlers.size
}

internal fun KernelContext.withRegistryJournal(
    journal: KernelRegistryMutationJournal,
    ownerModuleId: String
): KernelContext = copy(
    services = services.transactionalView(journal),
    capabilities = capabilities.transactionalView(journal, ownerModuleId),
    events = events.transactionalView(journal),
    commands = commands.transactionalView(journal)
)
