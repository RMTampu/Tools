package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicBoolean

public class ServiceHandle<T : Any> internal constructor(
    private val consumerOwner: ResourceOwner,
    private val providerOwner: ResourceOwner,
    private val value: T
) {
    public val available: Boolean get() = consumerOwner.isContextUsable() && providerOwner.isAcceptingInvocations()

    /**
     * Uses the service while holding both the consumer-generation context lease and provider
     * invocation lease. A handle cannot outlive either side of the route.
     */
    public fun <R> use(block: (service: T) -> R): R {
        val consumerPermit = consumerOwner.tryAcquireContextUse()
            ?: throw IllegalStateException(
                "Service consumer ${consumerOwner.token.id}#${consumerOwner.token.generation} is no longer active"
            )
        val providerPermit = providerOwner.tryAcquireInvocation()
        if (providerPermit == null) {
            consumerPermit.close()
            throw IllegalStateException("Service provider ${providerOwner.token.id} is not active")
        }
        return try {
            block(value)
        } finally {
            providerPermit.close()
            consumerPermit.close()
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

    public fun <T : Any> reference(type: Class<T>, qualifier: String = "default"): ServiceHandle<T>? =
        owner.withContextUse {
            val key = ServiceKey(type, qualifier)
            val registration = registry.reference(key) { token ->
                token.id == owner.token.id || token.id in allowedProviderIds
            } ?: return@withContextUse null
            ServiceHandle(owner, registration.owner, registration.value)
        }

    public fun <T : Any> unregister(type: Class<T>, qualifier: String = "default"): Boolean =
        registry.unregister(owner, ServiceKey(type, qualifier))
}

public class ModuleCapabilities internal constructor(
    private val owner: ResourceOwner,
    private val registry: CapabilityRegistry,
    providedCapabilities: Set<CapabilityDeclaration>,
    private val capabilityBindings: Map<String, String>
) {
    private val declared = providedCapabilities.associateBy { it.id }

    /**
     * Compatibility bridge for modules that explicitly publish descriptor-declared capabilities.
     * Descriptor declarations are already registered by the kernel; an identical registration is idempotent.
     */
    public fun register(capability: Capability, replace: Boolean = false): Unit {
        check(!owner.isAcceptingInvocations()) { "Capabilities are activation-stable and cannot change while module is STARTED" }
        val capabilityId = capability.id
        val capabilityVersion = capability.version
        val providerId = capability.providerModuleId
        val declaration = declared[capabilityId]
            ?: throw IllegalArgumentException("Capability $capabilityId was not declared by ${owner.token.id}")
        require(providerId == owner.token.id) {
            "Capability provider $providerId does not match ${owner.token.id}"
        }
        require(declaration.version == capabilityVersion) {
            "Capability $capabilityId version $capabilityVersion does not match declared version ${declaration.version}"
        }
        registry.register(
            owner,
            CapabilitySnapshot(capabilityId, capabilityVersion, providerId),
            replace
        )
    }

    public fun get(id: String): Capability? = owner.withContextUse {
        val provider = capabilityBindings[id] ?: if (declared.containsKey(id)) owner.token.id else return@withContextUse null
        registry.findActive(CapabilityRequirement.required(id), provider)
    }

    public fun all(): List<Capability> = owner.withContextUse {
        buildList {
            capabilityBindings.forEach { (id, provider) ->
                registry.findActive(CapabilityRequirement.required(id), provider)?.let(::add)
            }
            declared.keys.forEach { id ->
                registry.findActive(CapabilityRequirement.required(id), owner.token.id)?.let(::add)
            }
        }.distinctBy { Triple(it.id, it.version, it.providerModuleId) }
    }

    /** Descriptor-declared capabilities are immutable for the life of this module generation. */
    public fun unregister(id: String): Boolean {
        check(!owner.isAcceptingInvocations()) { "Capabilities are activation-stable and cannot change while module is STARTED" }
        if (id in declared) {
            throw IllegalStateException("Descriptor-declared capability $id cannot be unregistered from an active module scope")
        }
        return registry.unregister(owner, id)
    }
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

    public fun execute(command: KernelCommand): CommandResult = owner.withContextUse {
        bus.execute(command) { token ->
            token.id == owner.token.id || token.id in allowedProviderIds
        }
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

    public fun publish(topic: String, payload: Any? = null): Unit = owner.withContextUse {
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

    init {
        descriptor.providedCapabilities
            .sortedBy { it.id }
            .forEach { declaration -> capabilityRegistry.registerDeclared(lease, declaration) }
    }

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
