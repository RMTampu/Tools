package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal data class OwnedValue<T>(
    val owner: ResourceOwner,
    val value: T
)

internal data class CapabilitySnapshot(
    override val id: String,
    override val version: ModuleVersion,
    override val providerModuleId: String
) : Capability

internal class ServiceRegistry(
    private val mutationGuard: KernelMutationGuard,
    private val onMutation: () -> Unit
) {
    private val services =
        ConcurrentHashMap<ServiceKey<*>, ConcurrentHashMap<OwnerToken, OwnedValue<Any>>>()

    internal fun <T : Any> register(owner: ResourceOwner, key: ServiceKey<T>, service: T, replace: Boolean): Unit =
        mutationGuard.mutate {
            owner.assertContextOpen()
            val bucket = services.computeIfAbsent(key) { ConcurrentHashMap() }
            val replacement = OwnedValue(owner, service as Any)
            if (!replace) {
                check(bucket.putIfAbsent(owner.token, replacement) == null) {
                    "Service already registered by ${owner.token.id}: ${key.type.name}:${key.qualifier}"
                }
                onMutation()
                return@mutate
            }
            bucket[owner.token] = replacement
            onMutation()
        }

    internal fun unregister(owner: ResourceOwner, key: ServiceKey<*>): Boolean = mutationGuard.mutate {
        owner.assertContextOpen()
        val bucket = services[key] ?: return@mutate false
        val removed = bucket.remove(owner.token) != null
        if (bucket.isEmpty()) services.remove(key, bucket)
        if (removed) onMutation()
        removed
    }

    internal fun <T : Any> reference(
        key: ServiceKey<T>,
        providerAllowed: (OwnerToken) -> Boolean
    ): OwnedValue<T>? {
        val current = services[key]
            ?.values
            ?.asSequence()
            ?.filter { it.owner.isAcceptingInvocations() && providerAllowed(it.owner.token) }
            ?.sortedWith(
                compareBy<OwnedValue<Any>> { it.owner.token.id }
                    .thenByDescending { it.owner.token.generation }
            )
            ?.firstOrNull()
            ?: return null
        @Suppress("UNCHECKED_CAST")
        return OwnedValue(current.owner, key.type.cast(current.value))
    }

    internal fun removeOwner(owner: OwnerToken): Unit = mutationGuard.mutate {
        var changed = false
        services.forEach { (key, bucket) ->
            if (bucket.remove(owner) != null) changed = true
            if (bucket.isEmpty()) services.remove(key, bucket)
        }
        if (changed) onMutation()
    }

    internal val size: Int get() = services.values.sumOf { it.size }
}

internal class CapabilityRegistry(
    private val mutationGuard: KernelMutationGuard,
    private val onMutation: () -> Unit
) {
    private val capabilities = ConcurrentHashMap<String, ConcurrentHashMap<OwnerToken, OwnedValue<CapabilitySnapshot>>>()

    internal fun registerDeclared(owner: ResourceOwner, declaration: CapabilityDeclaration): Unit {
        registerSnapshot(
            owner,
            CapabilitySnapshot(declaration.id, declaration.version, owner.token.id),
            replace = false,
            allowIdentical = true
        )
    }

    internal fun register(owner: ResourceOwner, capability: Capability, replace: Boolean): Unit {
        owner.assertContextOpen()
        val snapshot = CapabilitySnapshot(
            id = capability.id,
            version = capability.version,
            providerModuleId = capability.providerModuleId
        )
        registerSnapshot(owner, snapshot, replace, allowIdentical = true)
    }

    private fun registerSnapshot(
        owner: ResourceOwner,
        capability: CapabilitySnapshot,
        replace: Boolean,
        allowIdentical: Boolean
    ): Unit = mutationGuard.mutate {
        owner.assertContextOpen()
        KernelIdentifiers.requireValid(capability.id, "Capability id")
        require(capability.providerModuleId == owner.token.id) {
            "Capability provider must match owner ${owner.token.id}"
        }
        val bucket = capabilities.computeIfAbsent(capability.id) { ConcurrentHashMap() }
        val replacement = OwnedValue(owner, capability)
        val existing = bucket[owner.token]
        if (!replace) {
            if (allowIdentical && existing?.value == capability) return@mutate
            check(bucket.putIfAbsent(owner.token, replacement) == null) {
                "Capability ${capability.id} already registered by ${owner.token.id}"
            }
            onMutation()
            return@mutate
        }
        bucket[owner.token] = replacement
        if (existing?.value != capability) onMutation()
    }

    internal fun unregister(owner: ResourceOwner, id: String): Boolean = mutationGuard.mutate {
        owner.assertContextOpen()
        val bucket = capabilities[id] ?: return@mutate false
        val removed = bucket.remove(owner.token) != null
        if (bucket.isEmpty()) capabilities.remove(id, bucket)
        if (removed) onMutation()
        removed
    }

    internal fun findOwned(owner: OwnerToken, id: String): Capability? =
        capabilities[id]?.get(owner)?.value

    internal fun findActive(requirement: CapabilityRequirement, providerId: String? = null): Capability? =
        capabilities[requirement.id]
            ?.values
            ?.asSequence()
            ?.filter { it.owner.isAcceptingInvocations() }
            ?.filter { providerId == null || it.owner.token.id == providerId }
            ?.map { it.value }
            ?.filter { requirement.versionRange.contains(it.version) }
            ?.sortedWith(compareByDescending<CapabilitySnapshot> { it.version }.thenBy { it.providerModuleId })
            ?.firstOrNull()

    internal fun allActive(): List<Capability> = capabilities.values
        .flatMap { it.values }
        .filter { it.owner.isAcceptingInvocations() }
        .map { it.value }
        .sortedWith(compareBy<CapabilitySnapshot> { it.id }.thenByDescending { it.version }.thenBy { it.providerModuleId })

    internal fun removeOwner(owner: OwnerToken): Unit = mutationGuard.mutate {
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
    private val mutationGuard: KernelMutationGuard,
    private val onMutation: () -> Unit
) {
    private data class Listener(val owner: ResourceOwner, val callback: (KernelEvent) -> Unit)

    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<Listener>>()

    internal fun subscribe(owner: ResourceOwner, topic: String, listener: (KernelEvent) -> Unit): Subscription {
        owner.assertContextOpen()
        requireValidTopic(topic)
        val record = Listener(owner, listener)
        val bucket = mutationGuard.mutate {
            val current = listeners.computeIfAbsent(topic) { CopyOnWriteArrayList() }
            current += record
            onMutation()
            current
        }
        return Subscription {
            mutationGuard.mutate {
                val removed = bucket.remove(record)
                if (bucket.isEmpty()) listeners.remove(topic, bucket)
                if (removed) onMutation()
            }
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
                is CallbackOutcome.TimedOut -> {
                    record.owner.trackTimedOut(outcome.completion)
                    logger.warn(
                        "Event listener owned by ${record.owner.token.id} timed out for ${event.topic}",
                        outcome.error
                    )
                }
            }
        }
    }

    internal fun removeOwner(owner: OwnerToken): Unit = mutationGuard.mutate {
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
    private val mutationGuard: KernelMutationGuard,
    private val onMutation: () -> Unit
) {
    private data class Handler(val owner: ResourceOwner, val callback: (KernelCommand) -> CommandResult)

    private val handlers = ConcurrentHashMap<String, Handler>()

    internal fun register(
        owner: ResourceOwner,
        commandName: String,
        replace: Boolean,
        handler: (KernelCommand) -> CommandResult
    ): Unit = mutationGuard.mutate {
        owner.assertContextOpen()
        KernelIdentifiers.requireValid(commandName, "Command name")
        val replacement = Handler(owner, handler)
        if (!replace) {
            check(handlers.putIfAbsent(commandName, replacement) == null) { "Command already registered: $commandName" }
            onMutation()
            return@mutate
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
        val commandName = try {
            command.name
        } catch (error: Throwable) {
            return CommandResult.failure(error)
        }
        return try {
            KernelIdentifiers.requireValid(commandName, "Command name")
            val handler = handlers[commandName]
                ?: return CommandResult.failure(IllegalArgumentException("No handler for command: $commandName"))
            if (!ownerAllowed(handler.owner.token)) {
                return CommandResult.failure(IllegalStateException("Command $commandName is not visible to this caller"))
            }
            val permit = handler.owner.tryAcquireInvocation()
                ?: return CommandResult.failure(IllegalStateException("Command $commandName is not active"))
            val outcome = supervisor.execute("command:${handler.owner.token.id}:$commandName", timeoutMillis) {
                try {
                    handler.callback(command)
                } finally {
                    permit.close()
                }
            }
            when (outcome) {
                is CallbackOutcome.Success -> outcome.value
                is CallbackOutcome.Failure -> {
                    permit.close()
                    CommandResult.failure(outcome.error)
                }
                is CallbackOutcome.TimedOut -> {
                    handler.owner.trackTimedOut(outcome.completion)
                    CommandResult.failure(outcome.error)
                }
            }
        } catch (error: Throwable) {
            CommandResult.failure(error)
        }
    }

    internal fun unregister(owner: ResourceOwner, commandName: String): Boolean = mutationGuard.mutate {
        owner.assertContextOpen()
        val current = handlers[commandName] ?: return@mutate false
        if (current.owner.token != owner.token) return@mutate false
        val removed = handlers.remove(commandName, current)
        if (removed) onMutation()
        removed
    }

    internal fun removeOwner(owner: OwnerToken): Unit = mutationGuard.mutate {
        val changed = handlers.entries.removeIf { it.value.owner.token == owner }
        if (changed) onMutation()
    }

    internal val size: Int get() = handlers.size
}
