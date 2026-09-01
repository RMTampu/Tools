package io.toolbox.kernel

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Rollback journal for one module activation attempt.
 *
 * Registry ownership itself is stored by the registries, not by an ever-growing history.
 * After a successful activation the journal drops its undo list; module-owned live entries
 * remain identifiable by owner id and are released deterministically on unload.
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
    private val releaseActions = linkedMapOf<String, () -> Unit>()
    private var state = State.OPEN
    private var rollbackTerminal = State.RELEASED

    fun <T> mutate(block: () -> Mutation<T>): T = synchronized(lock) {
        when (state) {
            State.OPEN -> {
                val mutation = block()
                mutation.undo?.let(undoActions::addFirst)
                mutation.result
            }

            State.COMMITTED -> block().result
            State.ROLLING_BACK,
            State.RELEASED -> error("Registry mutation attempted through an inactive module context")
        }
    }

    fun bindReleaseAction(key: String, action: () -> Unit) = synchronized(lock) {
        check(state != State.RELEASED) { "Cannot bind registry ownership after release" }
        releaseActions.putIfAbsent(key, action)
    }

    fun begin() = synchronized(lock) {
        check(state == State.COMMITTED) { "Registry mutation journal cannot begin from $state" }
        check(undoActions.isEmpty()) { "Registry mutation journal retained stale undo state" }
        rollbackTerminal = State.COMMITTED
        state = State.OPEN
    }

    fun commit() = synchronized(lock) {
        check(state == State.OPEN) { "Registry mutation journal cannot commit from $state" }
        undoActions.clear()
        rollbackTerminal = State.COMMITTED
        state = State.COMMITTED
    }

    fun rollbackIfOpen(): List<Throwable> = synchronized(lock) {
        when (state) {
            State.OPEN -> rollbackOpenLocked(releaseOwnership = rollbackTerminal == State.RELEASED)
            State.COMMITTED,
            State.RELEASED -> emptyList()
            State.ROLLING_BACK -> error("Registry mutation journal is already rolling back")
        }
    }

    fun releaseAll(): List<Throwable> = synchronized(lock) {
        when (state) {
            State.RELEASED -> emptyList()
            State.ROLLING_BACK -> error("Registry mutation journal is already rolling back")
            State.OPEN -> rollbackOpenLocked(releaseOwnership = true, forceReleased = true)
            State.COMMITTED -> releaseOwnershipLocked(State.COMMITTED)
        }
    }

    private fun rollbackOpenLocked(
        releaseOwnership: Boolean,
        forceReleased: Boolean = false
    ): List<Throwable> {
        state = State.ROLLING_BACK
        val failures = mutableListOf<Throwable>()
        while (undoActions.isNotEmpty()) {
            val action = undoActions.first()
            val result = runCatching { action.invoke() }
            if (result.isFailure) {
                failures += result.exceptionOrNull()!!
                state = State.OPEN
                return failures
            }
            undoActions.removeFirst()
        }

        if (releaseOwnership) {
            val releaseFailures = runReleaseActionsLocked()
            if (releaseFailures.isNotEmpty()) {
                state = State.OPEN
                return releaseFailures
            }
        }

        state = if (forceReleased || rollbackTerminal == State.RELEASED) {
            State.RELEASED
        } else {
            rollbackTerminal
        }
        return failures
    }

    private fun releaseOwnershipLocked(previousState: State): List<Throwable> {
        state = State.ROLLING_BACK
        val failures = runReleaseActionsLocked()
        if (failures.isEmpty()) {
            undoActions.clear()
            state = State.RELEASED
        } else {
            state = previousState
        }
        return failures
    }

    private fun runReleaseActionsLocked(): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        releaseActions.values.forEach { action ->
            runCatching { action.invoke() }
                .exceptionOrNull()
                ?.let(failures::add)
        }
        return failures
    }
}

private fun <T> applyRegistryMutation(
    journal: KernelRegistryMutationJournal?,
    block: () -> KernelRegistryMutationJournal.Mutation<T>
): T = journal?.mutate(block) ?: block().result

class ServiceRegistry private constructor(
    private val services: ConcurrentHashMap<Class<*>, ServiceEntry>,
    private val journal: KernelRegistryMutationJournal?,
    private val ownerModuleId: String?
) {
    private data class ServiceEntry(
        val service: Any,
        val ownerModuleId: String?
    )

    constructor() : this(ConcurrentHashMap(), null, null)

    internal fun transactionalView(
        journal: KernelRegistryMutationJournal,
        ownerModuleId: String
    ): ServiceRegistry = ServiceRegistry(services, journal, ownerModuleId)

    fun <T : Any> register(type: Class<T>, service: T, replace: Boolean = false) {
        val entry = ServiceEntry(service, ownerModuleId)
        applyRegistryMutation(journal) {
            if (replace) {
                var previous: ServiceEntry? = null
                services.compute(type) { _, current ->
                    requireOwnedOrHost(type.name, current?.ownerModuleId)
                    previous = current
                    entry
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    services.compute(type) { _, current ->
                        if (current === entry) previous else current
                    }
                }
            } else {
                check(services.putIfAbsent(type, entry) == null) {
                    "Service already registered: ${type.name}"
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    services.compute(type) { _, current ->
                        if (current === entry) null else current
                    }
                }
            }
        }
    }

    fun <T : Any> get(type: Class<T>): T? = services[type]?.service?.let(type::cast)

    fun unregister(type: Class<*>) {
        applyRegistryMutation(journal) {
            var removed: ServiceEntry? = null
            services.compute(type) { _, current ->
                if (current == null) {
                    null
                } else {
                    requireOwnedOrHost(type.name, current.ownerModuleId)
                    removed = current
                    null
                }
            }
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = removed?.let { previous ->
                    {
                        services.putIfAbsent(type, previous)
                        Unit
                    }
                }
            )
        }
    }

    internal fun releaseOwner(owner: String) {
        services.keys.toList().forEach { type ->
            services.computeIfPresent(type) { _, current ->
                if (current.ownerModuleId == owner) null else current
            }
        }
    }

    private fun requireOwnedOrHost(key: String, currentOwner: String?) {
        ownerModuleId?.let { owner ->
            check(currentOwner == owner) {
                "Service $key is not owned by module $owner"
            }
        }
    }

    val size: Int get() = services.size
}

class CapabilityRegistry private constructor(
    private val capabilities: ConcurrentHashMap<String, CapabilityEntry>,
    private val journal: KernelRegistryMutationJournal?,
    private val ownerModuleId: String?
) {
    private data class RegisteredCapability(
        override val id: String,
        override val version: Int,
        override val providerModuleId: String
    ) : Capability

    private data class CapabilityEntry(
        val capability: RegisteredCapability,
        val ownerModuleId: String?
    )

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

        val entry = CapabilityEntry(
            capability = RegisteredCapability(id, version, providerModuleId),
            ownerModuleId = ownerModuleId
        )
        applyRegistryMutation(journal) {
            if (replace) {
                var previous: CapabilityEntry? = null
                capabilities.compute(id) { _, current ->
                    requireOwnedOrHost(id, current?.ownerModuleId)
                    previous = current
                    entry
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    capabilities.compute(id) { _, current ->
                        if (current === entry) previous else current
                    }
                }
            } else {
                check(capabilities.putIfAbsent(id, entry) == null) {
                    "Capability already registered: $id"
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    capabilities.compute(id) { _, current ->
                        if (current === entry) null else current
                    }
                }
            }
        }
    }

    fun get(id: String): Capability? = capabilities[id]?.capability

    fun unregister(id: String) {
        applyRegistryMutation(journal) {
            var removed: CapabilityEntry? = null
            capabilities.compute(id) { _, current ->
                if (current == null) {
                    null
                } else {
                    requireOwnedOrHost(id, current.ownerModuleId)
                    removed = current
                    null
                }
            }
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = removed?.let { previous ->
                    {
                        capabilities.putIfAbsent(id, previous)
                        Unit
                    }
                }
            )
        }
    }

    internal fun releaseOwner(owner: String) {
        capabilities.keys.toList().forEach { id ->
            capabilities.computeIfPresent(id) { _, current ->
                if (current.ownerModuleId == owner) null else current
            }
        }
    }

    private fun requireOwnedOrHost(key: String, currentOwner: String?) {
        ownerModuleId?.let { owner ->
            check(currentOwner == owner) {
                "Capability $key is not owned by module $owner"
            }
        }
    }

    fun all(): List<Capability> = capabilities.values.map { it.capability }.sortedBy { it.id }

    val size: Int get() = capabilities.size
}

class EventBus private constructor(
    private val logger: KernelLogger,
    private val listeners: ConcurrentHashMap<String, CopyOnWriteArrayList<(KernelEvent) -> Unit>>,
    private val journal: KernelRegistryMutationJournal?,
    private val ownerModuleId: String?
) {
    private class OwnedListener(
        private val delegate: (KernelEvent) -> Unit,
        val ownerModuleId: String?
    ) : (KernelEvent) -> Unit {
        override fun invoke(event: KernelEvent) = delegate(event)
    }

    constructor(logger: KernelLogger = NoopKernelLogger) : this(logger, ConcurrentHashMap(), null, null)

    internal fun transactionalView(
        journal: KernelRegistryMutationJournal,
        ownerModuleId: String
    ): EventBus = EventBus(logger, listeners, journal, ownerModuleId)

    fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription {
        require(topic.isNotBlank()) { "Event topic cannot be blank" }
        val ownedListener: (KernelEvent) -> Unit = OwnedListener(listener, ownerModuleId)
        return applyRegistryMutation(journal) {
            var bucket: CopyOnWriteArrayList<(KernelEvent) -> Unit>? = null
            listeners.compute(topic) { _, current ->
                val selected = current ?: CopyOnWriteArrayList()
                selected += ownedListener
                bucket = selected
                selected
            }
            val selectedBucket = checkNotNull(bucket)
            val subscription = Subscription {
                removeSubscription(topic, selectedBucket, ownedListener)
            }
            KernelRegistryMutationJournal.Mutation(subscription) {
                removeListenerAtomically(topic, selectedBucket, ownedListener)
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
            KernelRegistryMutationJournal.Mutation(Unit, undo)
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

    internal fun releaseOwner(owner: String) {
        listeners.keys.toList().forEach { topic ->
            listeners.computeIfPresent(topic) { _, bucket ->
                bucket.removeIf { listener ->
                    listener is OwnedListener && listener.ownerModuleId == owner
                }
                if (bucket.isEmpty()) null else bucket
            }
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
    private val handlers: ConcurrentHashMap<String, CommandEntry>,
    private val journal: KernelRegistryMutationJournal?,
    private val ownerModuleId: String?
) {
    private data class CommandEntry(
        val handler: (KernelCommand) -> CommandResult,
        val ownerModuleId: String?
    )

    constructor() : this(ConcurrentHashMap(), null, null)

    internal fun transactionalView(
        journal: KernelRegistryMutationJournal,
        ownerModuleId: String
    ): CommandBus = CommandBus(handlers, journal, ownerModuleId)

    fun register(
        commandName: String,
        replace: Boolean = false,
        handler: (KernelCommand) -> CommandResult
    ) {
        require(commandName.isNotBlank()) { "Command name cannot be blank" }
        require(commandName.none(Char::isWhitespace)) { "Command name cannot contain whitespace" }
        val entry = CommandEntry(handler, ownerModuleId)
        applyRegistryMutation(journal) {
            if (replace) {
                var previous: CommandEntry? = null
                handlers.compute(commandName) { _, current ->
                    requireOwnedOrHost(commandName, current?.ownerModuleId)
                    previous = current
                    entry
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    handlers.compute(commandName) { _, current ->
                        if (current === entry) previous else current
                    }
                }
            } else {
                check(handlers.putIfAbsent(commandName, entry) == null) {
                    "Command already registered: $commandName"
                }
                KernelRegistryMutationJournal.Mutation(Unit) {
                    handlers.compute(commandName) { _, current ->
                        if (current === entry) null else current
                    }
                }
            }
        }
    }

    fun execute(command: KernelCommand): CommandResult {
        if (command.name.isBlank() || command.name.any(Char::isWhitespace)) {
            return CommandResult.failure(IllegalArgumentException("Command name is invalid"))
        }
        val handler = handlers[command.name]?.handler
            ?: return CommandResult.failure(IllegalArgumentException("No handler for command: ${command.name}"))
        return runCatching { handler(command) }
            .getOrElse(CommandResult::failure)
    }

    fun unregister(commandName: String) {
        applyRegistryMutation(journal) {
            var removed: CommandEntry? = null
            handlers.compute(commandName) { _, current ->
                if (current == null) {
                    null
                } else {
                    requireOwnedOrHost(commandName, current.ownerModuleId)
                    removed = current
                    null
                }
            }
            KernelRegistryMutationJournal.Mutation(
                result = Unit,
                undo = removed?.let { previous ->
                    {
                        handlers.putIfAbsent(commandName, previous)
                        Unit
                    }
                }
            )
        }
    }

    internal fun releaseOwner(owner: String) {
        handlers.keys.toList().forEach { commandName ->
            handlers.computeIfPresent(commandName) { _, current ->
                if (current.ownerModuleId == owner) null else current
            }
        }
    }

    private fun requireOwnedOrHost(key: String, currentOwner: String?) {
        ownerModuleId?.let { owner ->
            check(currentOwner == owner) {
                "Command $key is not owned by module $owner"
            }
        }
    }

    val size: Int get() = handlers.size
}

internal fun KernelContext.withRegistryJournal(
    journal: KernelRegistryMutationJournal,
    ownerModuleId: String
): KernelContext {
    journal.bindReleaseAction("services") { services.releaseOwner(ownerModuleId) }
    journal.bindReleaseAction("capabilities") { capabilities.releaseOwner(ownerModuleId) }
    journal.bindReleaseAction("events") { events.releaseOwner(ownerModuleId) }
    journal.bindReleaseAction("commands") { commands.releaseOwner(ownerModuleId) }

    return copy(
        services = services.transactionalView(journal, ownerModuleId),
        capabilities = capabilities.transactionalView(journal, ownerModuleId),
        events = events.transactionalView(journal, ownerModuleId),
        commands = commands.transactionalView(journal, ownerModuleId)
    )
}
