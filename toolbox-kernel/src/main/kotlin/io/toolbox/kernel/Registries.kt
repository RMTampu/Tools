package io.toolbox.kernel

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class KernelRegistryMutationJournal {
    internal data class Mutation<T>(
        val result: T,
        val undo: (() -> Unit)? = null
    )

    private enum class State {
        OPEN,
        COMMITTED,
        ROLLING_BACK,
        ROLLED_BACK
    }

    private val lock = Any()
    private val undoActions = ArrayDeque<() -> Unit>()
    private var state = State.OPEN

    fun <T> mutate(block: () -> Mutation<T>): T = synchronized(lock) {
        when (state) {
            State.OPEN -> {
                val mutation = block()
                mutation.undo?.let(undoActions::addFirst)
                mutation.result
            }

            State.COMMITTED -> block().result
            State.ROLLING_BACK,
            State.ROLLED_BACK -> error("Registry mutation attempted through a rolled-back activation context")
        }
    }

    fun begin() = synchronized(lock) {
        check(state == State.COMMITTED) { "Registry mutation journal cannot begin from $state" }
        undoActions.clear()
        state = State.OPEN
    }

    fun commit() = synchronized(lock) {
        check(state == State.OPEN) { "Registry mutation journal cannot commit from $state" }
        undoActions.clear()
        state = State.COMMITTED
    }

    fun rollback(): List<Throwable> = synchronized(lock) {
        when (state) {
            State.OPEN -> rollbackLocked()
            State.ROLLED_BACK -> emptyList()
            State.COMMITTED,
            State.ROLLING_BACK -> error("Registry mutation journal cannot rollback from $state")
        }
    }

    fun rollbackIfOpen(): List<Throwable> = synchronized(lock) {
        when (state) {
            State.OPEN -> rollbackLocked()
            State.COMMITTED,
            State.ROLLED_BACK -> emptyList()
            State.ROLLING_BACK -> error("Registry mutation journal is already rolling back")
        }
    }

    private fun rollbackLocked(): List<Throwable> {
        check(state == State.OPEN) { "Registry mutation journal cannot rollback from $state" }
        state = State.ROLLING_BACK
        val failures = mutableListOf<Throwable>()
        while (undoActions.isNotEmpty()) {
            runCatching { undoActions.removeFirst().invoke() }
                .exceptionOrNull()
                ?.let(failures::add)
        }
        state = State.ROLLED_BACK
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
                    { services.putIfAbsent(type, previous) }
                }
            )
        }
    }

    val size: Int get() = services.size
}

class CapabilityRegistry private constructor(
    private val capabilities: ConcurrentHashMap<String, Capability>,
    private val journal: KernelRegistryMutationJournal?
) {
    private data class RegisteredCapability(
        override val id: String,
        override val version: Int,
        override val providerModuleId: String
    ) : Capability

    constructor() : this(ConcurrentHashMap(), null)

    internal fun transactionalView(journal: KernelRegistryMutationJournal): CapabilityRegistry =
        CapabilityRegistry(capabilities, journal)

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
                    { capabilities.putIfAbsent(id, previous) }
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
            val bucket = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
            bucket += listener
            val subscription = Subscription {
                removeSubscription(bucket, listener)
            }
            KernelRegistryMutationJournal.Mutation(subscription) {
                bucket.remove(listener)
            }
        }
    }

    private fun removeSubscription(
        bucket: CopyOnWriteArrayList<(KernelEvent) -> Unit>,
        listener: (KernelEvent) -> Unit
    ) {
        applyRegistryMutation(journal) {
            val removed = bucket.remove(listener)
            if (bucket.isEmpty()) {
                // Keep the topic bucket stable. Removing an observed-empty bucket here
                // races a concurrent subscribe and can detach the newly added listener.
                // Empty buckets are lightweight metadata and are intentionally retained.
            }
            val undo: (() -> Unit)? = if (removed) {
                { bucket.add(listener); Unit }
            } else {
                null
            }
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = undo
            )
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
                    { handlers.putIfAbsent(commandName, previous) }
                }
            )
        }
    }

    val size: Int get() = handlers.size
}

internal fun KernelContext.withRegistryJournal(journal: KernelRegistryMutationJournal): KernelContext = copy(
    services = services.transactionalView(journal),
    capabilities = capabilities.transactionalView(journal),
    events = events.transactionalView(journal),
    commands = commands.transactionalView(journal)
)
