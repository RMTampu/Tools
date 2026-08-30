package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal data class OwnedValue<T>(
    val owner: ResourceOwner,
    val value: T
)

internal class ServiceRegistry(
    private val onMutation: () -> Unit
) {
    private val services = ConcurrentHashMap<ServiceKey<*>, OwnedValue<Any>>()

    internal fun <T : Any> register(owner: ResourceOwner, key: ServiceKey<T>, service: T, replace: Boolean): Unit {
        owner.assertContextOpen()
        val replacement = OwnedValue(owner, service as Any)
        if (!replace) {
            check(services.putIfAbsent(key, replacement) == null) {
                "Service already registered: ${key.type.name}:${key.qualifier}"
            }
            onMutation()
            return
        }
        services.compute(key) { _, current ->
            check(current == null || current.owner.token == owner.token) {
                "Service ${key.type.name}:${key.qualifier} is owned by ${current?.owner?.token?.id}"
            }
            replacement
        }
        onMutation()
    }

    internal fun unregister(owner: ResourceOwner, key: ServiceKey<*>): Boolean {
        owner.assertContextOpen()
        val current = services[key] ?: return false
        if (current.owner.token != owner.token) return false
        val removed = services.remove(key, current)
        if (removed) onMutation()
        return removed
    }

    internal fun <T : Any> reference(
        key: ServiceKey<T>,
        providerAllowed: (OwnerToken) -> Boolean
    ): OwnedValue<T>? {
        val current = services[key] ?: return null
        if (!current.owner.isAcceptingInvocations() || !providerAllowed(current.owner.token)) return null
        @Suppress("UNCHECKED_CAST")
        return OwnedValue(current.owner, key.type.cast(current.value))
    }

    internal fun removeOwner(owner: OwnerToken): Unit {
        val changed = services.entries.removeIf { it.value.owner.token == owner }
        if (changed) onMutation()
    }

    internal val size: Int get() = services.size
}

internal class CapabilityRegistry(
    private val onMutation: () -> Unit
) {
    private val capabilities = ConcurrentHashMap<String, ConcurrentHashMap<OwnerToken, OwnedValue<Capability>>>()

    internal fun register(owner: ResourceOwner, capability: Capability, replace: Boolean): Unit {
        owner.assertContextOpen()
        KernelIdentifiers.requireValid(capability.id, "Capability id")
        require(capability.providerModuleId == owner.token.id) { "Capability provider must match owner ${owner.token.id}" }
        val bucket = capabilities.computeIfAbsent(capability.id) { ConcurrentHashMap() }
        val replacement = OwnedValue(owner, capability)
        if (!replace) {
            check(bucket.putIfAbsent(owner.token, replacement) == null) {
                "Capability ${capability.id} already registered by ${owner.token.id}"
            }
            onMutation()
            return
        }
        bucket[owner.token] = replacement
        onMutation()
    }

    internal fun unregister(owner: ResourceOwner, id: String): Boolean {
        owner.assertContextOpen()
        val bucket = capabilities[id] ?: return false
        val removed = bucket.remove(owner.token) != null
        if (bucket.isEmpty()) capabilities.remove(id, bucket)
        if (removed) onMutation()
        return removed
    }

    internal fun findActive(requirement: CapabilityRequirement, providerId: String? = null): Capability? =
        capabilities[requirement.id]
            ?.values
            ?.asSequence()
            ?.filter { it.owner.isAcceptingInvocations() }
            ?.filter { providerId == null || it.owner.token.id == providerId }
            ?.map { it.value }
            ?.filter { requirement.versionRange.contains(it.version) }
            ?.sortedWith(compareByDescending<Capability> { it.version }.thenBy { it.providerModuleId })
            ?.firstOrNull()

    internal fun allActive(): List<Capability> = capabilities.values
        .flatMap { it.values }
        .filter { it.owner.isAcceptingInvocations() }
        .map { it.value }
        .sortedWith(compareBy<Capability> { it.id }.thenByDescending { it.version }.thenBy { it.providerModuleId })

    internal fun removeOwner(owner: OwnerToken): Unit {
        var changed = false
        capabilities.forEach { (id, bucket) ->
            if (bucket.remove(owner) != null) changed = true
            if (bucket.isEmpty()) capabilities.remove(id, bucket)
        }
        if (changed) onMutation()
    }

    internal val size: Int get() = capabilities.values.sumOf { it.size }
}

public fun interface Subscription : AutoCloseable {
    override fun close(): Unit
}

internal class EventBus(
    private val logger: KernelLogger,
    private val supervisor: CallbackSupervisor,
    private val listenerTimeoutMillis: Long,
    private val onMutation: () -> Unit
) {
    private data class Listener(val owner: ResourceOwner, val callback: (KernelEvent) -> Unit)

    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<Listener>>()

    internal fun subscribe(owner: ResourceOwner, topic: String, listener: (KernelEvent) -> Unit): Subscription {
        owner.assertContextOpen()
        requireValidTopic(topic)
        val record = Listener(owner, listener)
        val bucket = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        bucket += record
        onMutation()
        return Subscription {
            val removed = bucket.remove(record)
            if (bucket.isEmpty()) listeners.remove(topic, bucket)
            if (removed) onMutation()
        }
    }

    internal fun publish(event: KernelEvent): Unit {
        requireValidTopic(event.topic)
        deliver(listeners[event.topic], event)
        if (event.topic != WILDCARD) deliver(listeners[WILDCARD], event)
    }

    private fun deliver(bucket: List<Listener>?, event: KernelEvent): Unit {
        bucket?.forEach { record ->
            val permit = record.owner.tryAcquireInvocation() ?: return@forEach
            val outcome = supervisor.execute(
                "event:${record.owner.token.id}:${event.topic}",
                listenerTimeoutMillis
            ) {
                try {
                    record.callback(event)
                } finally {
                    permit.close()
                }
            }
            when (outcome) {
                is CallbackOutcome.Success -> Unit
                is CallbackOutcome.Failure -> {
                    permit.close()
                    logger.warn("Event listener owned by ${record.owner.token.id} failed for ${event.topic}", outcome.error)
                }
                is CallbackOutcome.TimedOut -> logger.warn(
                    "Event listener owned by ${record.owner.token.id} timed out for ${event.topic}",
                    outcome.error
                )
            }
        }
    }

    internal fun removeOwner(owner: OwnerToken): Unit {
        var changed = false
        listeners.forEach { (topic, bucket) ->
            if (bucket.removeIf { it.owner.token == owner }) changed = true
            if (bucket.isEmpty()) listeners.remove(topic, bucket)
        }
        if (changed) onMutation()
    }

    internal val size: Int get() = listeners.values.sumOf { it.size }

    private fun requireValidTopic(topic: String): Unit {
        require(topic == WILDCARD || KernelIdentifiers.isValid(topic)) { "Event topic is invalid: $topic" }
    }

    internal companion object {
        const val WILDCARD: String = "*"
    }
}

internal class CommandBus(
    private val supervisor: CallbackSupervisor,
    private val timeoutMillis: Long,
    private val onMutation: () -> Unit
) {
    private data class Handler(val owner: ResourceOwner, val callback: (KernelCommand) -> CommandResult)

    private val handlers = ConcurrentHashMap<String, Handler>()

    internal fun register(
        owner: ResourceOwner,
        commandName: String,
        replace: Boolean,
        handler: (KernelCommand) -> CommandResult
    ): Unit {
        owner.assertContextOpen()
        KernelIdentifiers.requireValid(commandName, "Command name")
        val replacement = Handler(owner, handler)
        if (!replace) {
            check(handlers.putIfAbsent(commandName, replacement) == null) { "Command already registered: $commandName" }
            onMutation()
            return
        }
        handlers.compute(commandName) { _, current ->
            check(current == null || current.owner.token == owner.token) {
                "Command $commandName is owned by ${current?.owner?.token?.id}"
            }
            replacement
        }
        onMutation()
    }

    internal fun execute(command: KernelCommand, ownerAllowed: (OwnerToken) -> Boolean = { true }): CommandResult {
        KernelIdentifiers.requireValid(command.name, "Command name")
        val handler = handlers[command.name]
            ?: return CommandResult.failure(IllegalArgumentException("No handler for command: ${command.name}"))
        if (!ownerAllowed(handler.owner.token)) {
            return CommandResult.failure(IllegalStateException("Command ${command.name} is not visible to this caller"))
        }
        val permit = handler.owner.tryAcquireInvocation()
            ?: return CommandResult.failure(IllegalStateException("Command ${command.name} is not active"))
        val outcome = supervisor.execute("command:${handler.owner.token.id}:${command.name}", timeoutMillis) {
            try {
                handler.callback(command)
            } finally {
                permit.close()
            }
        }
        return when (outcome) {
            is CallbackOutcome.Success -> outcome.value
            is CallbackOutcome.Failure -> {
                permit.close()
                CommandResult.failure(outcome.error)
            }
            is CallbackOutcome.TimedOut -> CommandResult.failure(outcome.error)
        }
    }

    internal fun unregister(owner: ResourceOwner, commandName: String): Boolean {
        owner.assertContextOpen()
        val current = handlers[commandName] ?: return false
        if (current.owner.token != owner.token) return false
        val removed = handlers.remove(commandName, current)
        if (removed) onMutation()
        return removed
    }

    internal fun removeOwner(owner: OwnerToken): Unit {
        val changed = handlers.entries.removeIf { it.value.owner.token == owner }
        if (changed) onMutation()
    }

    internal val size: Int get() = handlers.size
}
