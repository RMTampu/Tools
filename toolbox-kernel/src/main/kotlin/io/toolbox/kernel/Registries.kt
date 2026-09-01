package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> register(type: Class<T>, service: T, replace: Boolean = false) {
        if (replace) {
            services[type] = service
            return
        }
        check(services.putIfAbsent(type, service) == null) {
            "Service already registered: ${type.name}"
        }
    }

    fun <T : Any> get(type: Class<T>): T? = services[type]?.let(type::cast)

    fun unregister(type: Class<*>) {
        services.remove(type)
    }

    internal fun snapshotState(): Map<Class<*>, Any> = HashMap(services)

    internal fun restoreState(snapshot: Map<Class<*>, Any>) {
        services.clear()
        services.putAll(snapshot)
    }

    val size: Int get() = services.size
}

class CapabilityRegistry {
    private data class RegisteredCapability(
        override val id: String,
        override val version: Int,
        override val providerModuleId: String
    ) : Capability

    private val capabilities = ConcurrentHashMap<String, Capability>()

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
        if (replace) {
            capabilities[id] = registered
            return
        }
        check(capabilities.putIfAbsent(id, registered) == null) {
            "Capability already registered: $id"
        }
    }

    fun get(id: String): Capability? = capabilities[id]

    fun unregister(id: String) {
        capabilities.remove(id)
    }

    fun all(): List<Capability> = capabilities.values.sortedBy { it.id }

    internal fun snapshotState(): Map<String, Capability> = HashMap(capabilities)

    internal fun restoreState(snapshot: Map<String, Capability>) {
        capabilities.clear()
        capabilities.putAll(snapshot)
    }

    val size: Int get() = capabilities.size
}

class EventBus(
    private val logger: KernelLogger = NoopKernelLogger
) {
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(KernelEvent) -> Unit>>()

    fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription {
        require(topic.isNotBlank()) { "Event topic cannot be blank" }
        val bucket = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        bucket += listener
        return Subscription {
            bucket.remove(listener)
            if (bucket.isEmpty()) {
                // Keep the topic bucket stable. Removing an observed-empty bucket here
                // races a concurrent subscribe and can detach the newly added listener.
                // Empty buckets are lightweight metadata and are intentionally retained.
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

    internal fun snapshotState(): Map<String, List<(KernelEvent) -> Unit>> =
        listeners.entries.associate { (topic, bucket) -> topic to bucket.toList() }

    internal fun restoreState(snapshot: Map<String, List<(KernelEvent) -> Unit>>) {
        listeners.forEach { (topic, bucket) ->
            if (topic !in snapshot) {
                bucket.clear()
            }
        }
        snapshot.forEach { (topic, snapshotListeners) ->
            val bucket = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
            bucket.clear()
            bucket.addAll(snapshotListeners)
        }
    }

    fun interface Subscription : AutoCloseable {
        override fun close()
    }

    companion object {
        const val WILDCARD = "*"
    }
}

class CommandBus {
    private val handlers = ConcurrentHashMap<String, (KernelCommand) -> CommandResult>()

    fun register(
        commandName: String,
        replace: Boolean = false,
        handler: (KernelCommand) -> CommandResult
    ) {
        require(commandName.isNotBlank()) { "Command name cannot be blank" }
        require(commandName.none(Char::isWhitespace)) { "Command name cannot contain whitespace" }
        if (replace) {
            handlers[commandName] = handler
            return
        }
        check(handlers.putIfAbsent(commandName, handler) == null) {
            "Command already registered: $commandName"
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
        handlers.remove(commandName)
    }

    internal fun snapshotState(): Map<String, (KernelCommand) -> CommandResult> = HashMap(handlers)

    internal fun restoreState(snapshot: Map<String, (KernelCommand) -> CommandResult>) {
        handlers.clear()
        handlers.putAll(snapshot)
    }

    val size: Int get() = handlers.size
}

internal data class KernelRegistryCheckpoint(
    val services: Map<Class<*>, Any>,
    val capabilities: Map<String, Capability>,
    val listeners: Map<String, List<(KernelEvent) -> Unit>>,
    val commands: Map<String, (KernelCommand) -> CommandResult>
)

internal fun KernelContext.captureRegistryCheckpoint(): KernelRegistryCheckpoint = KernelRegistryCheckpoint(
    services = services.snapshotState(),
    capabilities = capabilities.snapshotState(),
    listeners = events.snapshotState(),
    commands = commands.snapshotState()
)

internal fun KernelContext.restoreRegistryCheckpoint(checkpoint: KernelRegistryCheckpoint) {
    services.restoreState(checkpoint.services)
    capabilities.restoreState(checkpoint.capabilities)
    events.restoreState(checkpoint.listeners)
    commands.restoreState(checkpoint.commands)
}
