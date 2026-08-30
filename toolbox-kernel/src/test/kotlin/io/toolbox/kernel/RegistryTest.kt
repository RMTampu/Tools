package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistryTest {
    @Test
    fun `stopped module context is revoked and old generation cannot register after restart`() {
        val contexts = mutableListOf<KernelContext>()
        val kernel = ToolBoxKernel()
        kernel.install(module("owner", onLoadBlock = { contexts += it }))
        assertTrue(kernel.start().isSuccess)
        val first = contexts.single()
        assertTrue(kernel.stop().isSuccess)
        assertFailsWith<IllegalStateException> {
            first.services.register(String::class.java, "stale")
        }
        assertTrue(kernel.start().isSuccess)
        val second = contexts.last()
        second.services.register(String::class.java, "fresh")
        assertFailsWith<IllegalStateException> {
            first.commands.register("stale.command") { CommandResult.success() }
        }
        assertEquals("fresh", kernel.service(String::class.java)?.use { it })
    }

    @Test
    fun `stopped module event listener is removed and cannot receive later kernel events`() {
        var eventsSeen = 0
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "listener",
                onLoadBlock = { context -> context.events.subscribe("*") { eventsSeen++ } }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertTrue(kernel.stop().isSuccess)
        val afterStop = eventsSeen
        assertTrue(kernel.install(module("later")).isSuccess)
        assertEquals(afterStop, eventsSeen)
    }

    @Test
    fun `service handle is revocable after provider stop`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("provider", onLoadBlock = { it.services.register(String::class.java, "value") }))
        assertTrue(kernel.start().isSuccess)
        val handle = kernel.service(String::class.java) ?: error("missing service")
        assertEquals("value", handle.use { it })
        assertTrue(kernel.stopModule("provider").isSuccess)
        assertFalse(handle.available)
        assertFailsWith<IllegalStateException> {
            handle.use { value -> check(value.isNotEmpty()) }
        }
        assertNull(kernel.service(String::class.java))
    }

    @Test
    fun `module cannot consume undeclared provider service`() {
        var observed: String? = "unset"
        val kernel = ToolBoxKernel()
        kernel.install(module("a-provider", onLoadBlock = { it.services.register(String::class.java, "secret") }))
        kernel.install(
            module(
                "z-consumer",
                onLoadBlock = { context -> observed = context.services.reference(String::class.java)?.use { it } }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertNull(observed)
    }

    @Test
    fun `declared provider service is visible only after provider is started`() {
        var observed: String? = null
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                onLoadBlock = { it.services.register(String::class.java, "service"); calls += "provider-load" },
                onStartBlock = { calls += "provider-start" }
            )
        )
        kernel.install(
            module(
                "consumer",
                dependencies = setOf(ModuleDependency.required("provider")),
                onLoadBlock = { context ->
                    calls += "consumer-load"
                    observed = context.services.reference(String::class.java)?.use { it }
                }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertEquals("service", observed)
        assertTrue(calls.indexOf("provider-start") < calls.indexOf("consumer-load"))
    }

    @Test
    fun `stop drains in flight command before onStop`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = mutableListOf<String>()
        val config = KernelConfig(invocationDrainTimeoutMillis = 2_000, commandTimeoutMillis = 2_000)
        val kernel = ToolBoxKernel(config)
        kernel.install(
            module(
                "worker",
                onLoadBlock = { context ->
                    context.commands.register("worker.run") {
                        order += "command-start"
                        entered.countDown()
                        release.await(1, TimeUnit.SECONDS)
                        order += "command-end"
                        CommandResult.success()
                    }
                },
                onStopBlock = { order += "stop" }
            )
        )
        assertTrue(kernel.start().isSuccess)

        val commandResult = AtomicReference<CommandResult>()
        val caller = Thread { commandResult.set(kernel.execute(command("worker.run"))) }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        val stopResult = AtomicReference<KernelResult<Unit>>()
        val stopper = Thread { stopResult.set(kernel.stopModule("worker")) }
        stopper.start()
        Thread.sleep(50)
        assertFalse("stop" in order)
        release.countDown()
        caller.join(2_000)
        stopper.join(2_000)

        assertTrue(commandResult.get().success)
        assertTrue(stopResult.get().isSuccess)
        assertTrue(order.indexOf("command-end") < order.indexOf("stop"))
    }

    @Test
    fun `wildcard topic is delivered once per publication`() {
        var count = 0
        val kernel = ToolBoxKernel()
        kernel.subscribe("*") { event -> if (event.topic == "*") count++ }
        kernel.install(module("publisher", onLoadBlock = { it.events.publish("*") }))
        assertTrue(kernel.start().isSuccess)
        assertEquals(1, count)
    }
}
