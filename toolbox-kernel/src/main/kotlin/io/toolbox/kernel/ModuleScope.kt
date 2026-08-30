package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicBoolean

public class ServiceHandle<T : Any> internal constructor(
    private val owner: ResourceOwner,
    private val value: T
) {
    public val available: Boolean get() = owner.isAcceptingInvocations()

    /**
     * Uses the service while holding a provider invocation lease. Callers must not retain [service]
     * beyond the callback because the provider can be stopped immediately after this method returns.
     */
    public fun <R> use(block: (service: T) -> R): R {
        val permit = owner.tryAcquireInvocation()
            ?: throw IllegalStateException("Service provider ${owner.token.id} is not active")
        return try {
            block(value)
        } finally {
            permit.close()
        }
    }
}

public class ModuleServices internal constructor(
    private val owner: ResourceOwner,
    private val registry: ServiceRegistry,
    private val allowedProviderIds: Set<String>
) {
    public fun <T : Any> register(
        type: Class<T>,
        service: T,
        qualifier: String = "default",
        replace: Boolean = false
    ): Unit = registry.register(owner, ServiceKey(type, qualifier), service, replace)

    public fun <T : Any> reference(type: Class<T>, qualifier: String = "default"): ServiceHandle<T>? {
        val key = ServiceKey(type, qualifier)
        val registration = registry.reference(key) { token -> token.id == owner.token.id || token.id in allowedProviderIds }
            ?: return null
        return ServiceHandle(registration.owner, registration.value)
    }

    public fun unregister(type: Class<*>, qualifier: String = "default"): Boolean =
        registry.unregister(owner, ServiceKey(type, qualifier))
}

public class ModuleCapabilities internal constructor(
    private val owner: ResourceOwner,
    private val registry: CapabilityRegistry,
    providedCapabilities: Set<CapabilityDeclaration>,
    private val capabilityBindings: Map<String, String>
) {
    private val declared = providedCapabilities.associateBy { it.id }

    public fun register(capability: Capability, replace: Boolean = false): Unit {
        val declaration = declared[capability.id]
            ?: throw IllegalArgumentException("Capability ${capability.id} was not declared by ${owner.token.id}")
        require(declaration.version == capability.version) {
            "Capability ${capability.id} version ${capability.version} does not match declared version ${declaration.version}"
        }
        registry.register(owner, capability, replace)
    }

    public fun get(id: String): Capability? {
        val provider = capabilityBindings[id] ?: if (declared.containsKey(id)) owner.token.id else return null
        return registry.findActive(CapabilityRequirement.required(id), provider)
    }

    public fun all(): List<Capability> = buildList {
        capabilityBindings.forEach { (id, provider) ->
            registry.findActive(CapabilityRequirement.required(id), provider)?.let(::add)
        }
        declared.keys.forEach { id ->
            registry.findActive(CapabilityRequirement.required(id), owner.token.id)?.let(::add)
        }
    }.distinctBy { Triple(it.id, it.version, it.providerModuleId) }

    public fun unregister(id: String): Boolean = registry.unregister(owner, id)
}

public class ModuleCommands internal constructor(
    private val owner: ResourceOwner,
    private val bus: CommandBus,
    private val allowedProviderIds: Set<String>
) {
    public fun register(
        commandName: String,
        replace: Boolean = false,
        handler: (KernelCommand) -> CommandResult
    ): Unit = bus.register(owner, commandName, replace, handler)

    public fun execute(command: KernelCommand): CommandResult = bus.execute(command) { token ->
        token.id == owner.token.id || token.id in allowedProviderIds
    }

    public fun unregister(commandName: String): Boolean = bus.unregister(owner, commandName)
}

public class ModuleEvents internal constructor(
    private val owner: ResourceOwner,
    private val bus: EventBus,
    private val clock: KernelClock
) {
    public fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription =
        bus.subscribe(owner, topic, listener)

    public fun publish(topic: String, payload: Any? = null): Unit {
        owner.assertContextOpen()
        bus.publish(KernelEvent(topic, owner.token.id, payload, clock.nowMillis()))
    }
}

internal class ModuleScope(
    internal val lease: ModuleLease,
    descriptor: ModuleDescriptor,
    allowedProviderIds: Set<String>,
    capabilityBindings: Map<String, String>,
    servicesRegistry: ServiceRegistry,
    capabilityRegistry: CapabilityRegistry,
    eventBus: EventBus,
    commandBus: CommandBus,
    clock: KernelClock
) : AutoCloseable {
    internal val services = ModuleServices(lease, servicesRegistry, allowedProviderIds)
    internal val capabilities = ModuleCapabilities(lease, capabilityRegistry, descriptor.providedCapabilities, capabilityBindings)
    internal val events = ModuleEvents(lease, eventBus, clock)
    internal val commands = ModuleCommands(lease, commandBus, allowedProviderIds)

    private val closed = AtomicBoolean(false)
    private val serviceRegistry = servicesRegistry
    private val capabilityRegistryRef = capabilityRegistry
    private val eventBusRef = eventBus
    private val commandBusRef = commandBus

    override fun close(): Unit {
        if (!closed.compareAndSet(false, true)) return
        lease.closeContext()
        val token = lease.token
        eventBusRef.removeOwner(token)
        commandBusRef.removeOwner(token)
        capabilityRegistryRef.removeOwner(token)
        serviceRegistry.removeOwner(token)
    }
}
