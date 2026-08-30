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

    val size: Int get() = services.size
}

class CapabilityRegistry {
    private val capabilities = ConcurrentHashMap<String, Capability>()

    fun register(capability: Capability, replace: Boolean = false) {
        require(capability.id.isNotBlank()) { "Capability id cannot be blank" }
        if (replace) {
            capabilities[capability.id] = capability
            return
        }
        check(capabilities.putIfAbsent(capability.id, capability) == null) {
            "Capability already registered: ${capability.id}"
        }
    }

    fun get(id: String): Capability? = capabilities[id]

    fun unregister(id: String) {
        capabilities.remove(id)
    }

    fun all(): List<Capability> = capabilities.values.sortedBy { it.id }

    val size: Int get() = capabilities.size
}

class EventBus {
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(KernelEvent) -> Unit>>()

    fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription {
        val bucket = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        bucket += listener
        return Subscription { bucket.remove(listener) }
    }

    fun publish(event: KernelEvent) {
        listeners[event.topic]?.forEach { listener ->
            runCatching { listener(event) }
        }
        listeners[WILDCARD]?.forEach { listener ->
            runCatching { listener(event) }
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
        if (replace) {
            handlers[commandName] = handler
            return
        }
        check(handlers.putIfAbsent(commandName, handler) == null) {
            "Command already registered: $commandName"
        }
    }

    fun execute(command: KernelCommand): CommandResult {
        val handler = handlers[command.name]
            ?: return CommandResult.failure(IllegalArgumentException("No handler for command: ${command.name}"))
        return runCatching { handler(command) }
            .getOrElse(CommandResult::failure)
    }

    fun unregister(commandName: String) {
        handlers.remove(commandName)
    }

    val size: Int get() = handlers.size
}
