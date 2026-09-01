package io.toolbox.enginehost

import io.toolbox.kernel.Capability
import io.toolbox.kernel.EventBus
import io.toolbox.kernel.KernelContext
import io.toolbox.kernel.KernelEvent

class EngineRuntimeScope internal constructor(
    val engineId: String,
    private val kernel: KernelContext
) : AutoCloseable {
    private val cleanup = ArrayDeque<() -> Unit>()
    private var closed = false

    @Synchronized
    fun registerCapability(capability: Capability) {
        checkOpen()
        require(capability.providerModuleId == engineId) {
            "Capability ${capability.id} must declare providerModuleId=$engineId"
        }
        kernel.capabilities.register(capability)
        cleanup.addFirst {
            if (kernel.capabilities.get(capability.id) === capability) {
                kernel.capabilities.unregister(capability.id)
            }
        }
    }

    @Synchronized
    fun <T : Any> registerService(type: Class<T>, service: T) {
        checkOpen()
        kernel.services.register(type, service)
        cleanup.addFirst {
            if (kernel.services.get(type) === service) {
                kernel.services.unregister(type)
            }
        }
    }

    @Synchronized
    fun subscribe(topic: String, listener: (KernelEvent) -> Unit): EventBus.Subscription {
        checkOpen()
        val subscription = kernel.events.subscribe(topic, listener)
        cleanup.addFirst { subscription.close() }
        return subscription
    }

    @Synchronized
    fun publish(topic: String, payload: Any? = null) {
        checkOpen()
        kernel.events.publish(KernelEvent(topic = topic, source = engineId, payload = payload))
    }

    @Synchronized
    fun findCapability(id: String): Capability? {
        checkOpen()
        return kernel.capabilities.get(id)
    }

    @Synchronized
    fun <T : Any> findService(type: Class<T>): T? {
        checkOpen()
        return kernel.services.get(type)
    }

    @Synchronized
    fun putState(key: String, value: String) {
        checkOpen()
        kernel.ports.stateStore.put(stateKey(key), value)
    }

    @Synchronized
    fun getState(key: String): String? {
        checkOpen()
        return kernel.ports.stateStore.get(stateKey(key))
    }

    @Synchronized
    fun removeState(key: String) {
        checkOpen()
        kernel.ports.stateStore.remove(stateKey(key))
    }

    @Synchronized
    fun stateKeys(prefix: String = ""): Set<String> {
        checkOpen()
        require(!prefix.startsWith(".")) { "State prefix cannot start with '.'" }
        val namespace = "engine.$engineId."
        return kernel.ports.stateStore.keys(namespace + prefix)
            .mapTo(linkedSetOf()) { it.removePrefix(namespace) }
    }

    override fun close() {
        val actions: List<() -> Unit>
        synchronized(this) {
            if (closed) return
            closed = true
            actions = cleanup.toList()
            cleanup.clear()
        }

        var firstFailure: Throwable? = null
        actions.forEach { action ->
            runCatching(action).onFailure { error ->
                if (firstFailure == null) {
                    firstFailure = error
                } else {
                    firstFailure?.addSuppressed(error)
                }
            }
        }
        firstFailure?.let { throw it }
    }

    private fun checkOpen() {
        check(!closed) { "Engine runtime scope is closed: $engineId" }
    }

    private fun stateKey(key: String): String {
        require(key.isNotBlank()) { "State key cannot be blank" }
        require(!key.startsWith(".")) { "State key cannot start with '.'" }
        return "engine.$engineId.$key"
    }
}
