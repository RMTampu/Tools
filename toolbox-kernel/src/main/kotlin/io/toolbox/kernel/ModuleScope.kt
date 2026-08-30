package io.toolbox.kernel

public class ModuleServices internal constructor(
    private val owner: String,
    private val registry: ServiceRegistry
) {
    public fun <T : Any> register(type: Class<T>, service: T, replace: Boolean = false): Unit =
        registry.register(owner, type, service, replace)

    public fun <T : Any> get(type: Class<T>): T? = registry.get(type)

    public fun unregister(type: Class<*>): Boolean = registry.unregister(owner, type)
}

public class ModuleCapabilities internal constructor(
    private val owner: String,
    private val registry: CapabilityRegistry
) {
    public fun register(capability: Capability, replace: Boolean = false): Unit = registry.register(owner, capability, replace)
    public fun get(id: String): Capability? = registry.get(id)
    public fun all(): List<Capability> = registry.all()
    public fun unregister(id: String): Boolean = registry.unregister(owner, id)
}

public class ModuleCommands internal constructor(
    private val owner: String,
    private val bus: CommandBus
) {
    public fun register(
        commandName: String,
        replace: Boolean = false,
        handler: (KernelCommand) -> CommandResult
    ): Unit = bus.register(owner, commandName, replace, handler)

    public fun execute(command: KernelCommand): CommandResult = bus.execute(command)
    public fun unregister(commandName: String): Boolean = bus.unregister(owner, commandName)
}

public class ModuleEvents internal constructor(
    private val owner: String,
    private val bus: EventBus,
    private val clock: KernelClock
) {
    public fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription = bus.subscribe(owner, topic, listener)

    public fun publish(topic: String, payload: Any? = null): Unit {
        require(topic.isNotBlank()) { "Event topic cannot be blank" }
        bus.publish(KernelEvent(topic, owner, payload, clock.nowMillis()))
    }
}

internal class ModuleScope(
    owner: String,
    servicesRegistry: ServiceRegistry,
    capabilityRegistry: CapabilityRegistry,
    eventBus: EventBus,
    commandBus: CommandBus,
    clock: KernelClock
) : AutoCloseable {
    internal val services = ModuleServices(owner, servicesRegistry)
    internal val capabilities = ModuleCapabilities(owner, capabilityRegistry)
    internal val events = ModuleEvents(owner, eventBus, clock)
    internal val commands = ModuleCommands(owner, commandBus)

    private val ownerId = owner
    private val serviceRegistry = servicesRegistry
    private val capabilityRegistryRef = capabilityRegistry
    private val eventBusRef = eventBus
    private val commandBusRef = commandBus

    override fun close(): Unit {
        eventBusRef.removeOwner(ownerId)
        commandBusRef.removeOwner(ownerId)
        capabilityRegistryRef.removeOwner(ownerId)
        serviceRegistry.removeOwner(ownerId)
    }
}
