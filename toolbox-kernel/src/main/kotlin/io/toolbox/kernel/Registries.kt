package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal data class OwnedValue<T>(val owner: String, val value: T)

internal class ServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, OwnedValue<Any>>()

    internal fun <T : Any> register(owner: String, type: Class<T>, service: T, replace: Boolean): Unit {
        val replacement = OwnedValue(owner, service as Any)
        if (!replace) {
            check(services.putIfAbsent(type, replacement) == null) { "Service already registered: ${type.name}" }
            return
        }
        services.compute(type) { _, current ->
            check(current == null || current.owner == owner) { "Service ${type.name} is owned by ${current?.owner}" }
            replacement
        }
    }

    internal fun <T : Any> get(type: Class<T>): T? = services[type]?.value?.let(type::cast)

    internal fun unregister(owner: String, type: Class<*>): Boolean = services.computeIfPresent(type) { _, current ->
        if (current.owner == owner) null else current
    } == null

    internal fun removeOwner(owner: String): Unit {
        services.entries.removeIf { it.value.owner == owner }
    }

    internal val size: Int get() = services.size
}

internal class CapabilityRegistry {
    private val capabilities = ConcurrentHashMap<String, OwnedValue<Capability>>()

    internal fun register(owner: String, capability: Capability, replace: Boolean): Unit {
        require(capability.id.isNotBlank()) { "Capability id cannot be blank" }
        require(capability.providerModuleId == owner) { "Capability provider must match owner $owner" }
        val replacement = OwnedValue(owner, capability)
        if (!replace) {
            check(capabilities.putIfAbsent(capability.id, replacement) == null) { "Capability already registered: ${capability.id}" }
            return
        }
        capabilities.compute(capability.id) { _, current ->
            check(current == null || current.owner == owner) { "Capability ${capability.id} is owned by ${current?.owner}" }
            replacement
        }
    }

    internal fun get(id: String): Capability? = capabilities[id]?.value

    internal fun unregister(owner: String, id: String): Boolean = capabilities.computeIfPresent(id) { _, current ->
        if (current.owner == owner) null else current
    } == null

    internal fun removeOwner(owner: String): Unit {
        capabilities.entries.removeIf { it.value.owner == owner }
    }

    internal fun all(): List<Capability> = capabilities.values.map { it.value }.sortedBy { it.id }

    internal val size: Int get() = capabilities.size
}

public fun interface Subscription : AutoCloseable {
    override fun close(): Unit
}

internal class EventBus(private val logger: KernelLogger) {
    private data class Listener(val owner: String, val callback: (KernelEvent) -> Unit)

    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<Listener>>()

    internal fun subscribe(owner: String, topic: String, listener: (KernelEvent) -> Unit): Subscription {
        require(topic.isNotBlank()) { "Event topic cannot be blank" }
        val record = Listener(owner, listener)
        val bucket = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        bucket += record
        return Subscription {
            bucket.remove(record)
            if (bucket.isEmpty()) listeners.remove(topic, bucket)
        }
    }

    internal fun publish(event: KernelEvent): Unit {
        deliver(listeners[event.topic], event)
        if (event.topic != WILDCARD) deliver(listeners[WILDCARD], event)
    }

    private fun deliver(bucket: List<Listener>?, event: KernelEvent): Unit {
        bucket?.forEach { record ->
            runCatching { record.callback(event) }
                .onFailure { logger.warn("Event listener owned by ${record.owner} failed for ${event.topic}", it) }
        }
    }

    internal fun removeOwner(owner: String): Unit {
        listeners.forEach { (topic, bucket) ->
            bucket.removeIf { it.owner == owner }
            if (bucket.isEmpty()) listeners.remove(topic, bucket)
        }
    }

    internal val size: Int get() = listeners.values.sumOf { it.size }

    internal companion object {
        const val WILDCARD: String = "*"
    }
}

internal class CommandBus {
    private data class Handler(val owner: String, val callback: (KernelCommand) -> CommandResult)

    private val handlers = ConcurrentHashMap<String, Handler>()

    internal fun register(
        owner: String,
        commandName: String,
        replace: Boolean,
        handler: (KernelCommand) -> CommandResult
    ): Unit {
        require(commandName.isNotBlank()) { "Command name cannot be blank" }
        val replacement = Handler(owner, handler)
        if (!replace) {
            check(handlers.putIfAbsent(commandName, replacement) == null) { "Command already registered: $commandName" }
            return
        }
        handlers.compute(commandName) { _, current ->
            check(current == null || current.owner == owner) { "Command $commandName is owned by ${current?.owner}" }
            replacement
        }
    }

    internal fun execute(command: KernelCommand): CommandResult {
        val handler = handlers[command.name]
            ?: return CommandResult.failure(IllegalArgumentException("No handler for command: ${command.name}"))
        return runCatching { handler.callback(command) }.getOrElse(CommandResult::failure)
    }

    internal fun unregister(owner: String, commandName: String): Boolean = handlers.computeIfPresent(commandName) { _, current ->
        if (current.owner == owner) null else current
    } == null

    internal fun removeOwner(owner: String): Unit {
        handlers.entries.removeIf { it.value.owner == owner }
    }

    internal val size: Int get() = handlers.size
}
