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
        require(capability.id.none(Char::isWhitespace)) { "Capability id cannot contain whitespace" }
        require(capability.version > 0) { "Capability version must be positive" }
        require(capability.providerModuleId.isNotBlank()) { "Capability provider module id cannot be blank" }
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
                listeners.remove(topic, bucket)
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
                .onFailure { logger.warn("Event listener failed for topic ${event.topic}", it) }
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

    val size: Int get() = handlers.size
}
