package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResourceBudgetTest {
    @Test
    fun `extension saturation cannot consume lifecycle capacity and one owner cannot monopolize extensions`() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(2)
        val limits = KernelRuntimeLimits(
            maxLifecycleCallbacks = 1,
            maxExtensionCallbacks = 2,
            maxExtensionCallbacksPerOwner = 1,
            maxEventSubscriptions = 8,
            maxEventSubscriptionsPerOwner = 4,
            eventDispatchTimeoutMillis = 100
        )
        val supervisor = CallbackSupervisor(
            BudgetedKernelExecutor(DirectKernelExecutor, limits),
            NoopKernelLogger
        )
        val first = OwnerToken("first", 1)
        val second = OwnerToken("second", 1)
        val firstOutcome = AtomicReference<CallbackOutcome<Unit>>()
        val secondOutcome = AtomicReference<CallbackOutcome<Unit>>()

        fun launch(owner: OwnerToken, result: AtomicReference<CallbackOutcome<Unit>>): Thread = Thread {
            result.set(
                supervisor.executeExtension(owner, "command:${owner.id}:hang", 50) {
                    entered.countDown()
                    waitIgnoringInterrupts(release)
                }
            )
        }.apply { start() }

        val firstThread = launch(first, firstOutcome)
        val secondThread = launch(second, secondOutcome)
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        firstThread.join(1_000)
        secondThread.join(1_000)
        assertIs<CallbackOutcome.TimedOut>(firstOutcome.get())
        assertIs<CallbackOutcome.TimedOut>(secondOutcome.get())

        val sameOwner = supervisor.executeExtension(first, "command:first:again", 50) { Unit }
        assertIs<CallbackOutcome.Failure>(sameOwner)
        assertIs<CallbackCapacityException>(sameOwner.error)

        val thirdOwner = supervisor.executeExtension(OwnerToken("third", 1), "command:third:run", 50) { Unit }
        assertIs<CallbackOutcome.Failure>(thirdOwner)
        assertIs<CallbackCapacityException>(thirdOwner.error)

        val lifecycle = supervisor.execute("module:healthy:load", 200) { "ok" }
        assertIs<CallbackOutcome.Success<String>>(lifecycle)

        release.countDown()
    }

    @Test
    fun `event listener that timed out cannot be reentered while its callback is still alive`() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val invocations = AtomicInteger(0)
        val limits = KernelRuntimeLimits(
            maxLifecycleCallbacks = 1,
            maxExtensionCallbacks = 2,
            maxExtensionCallbacksPerOwner = 1,
            maxEventSubscriptions = 8,
            maxEventSubscriptionsPerOwner = 4,
            eventDispatchTimeoutMillis = 100
        )
        val supervisor = CallbackSupervisor(BudgetedKernelExecutor(DirectKernelExecutor, limits), NoopKernelLogger)
        val bus = EventBus(NoopKernelLogger, supervisor, 40, KernelMutationGuard()) { }
        val owner = startedOwner("listener")
        bus.subscribe(owner, "test.event") {
            invocations.incrementAndGet()
            entered.countDown()
            waitIgnoringInterrupts(release)
        }

        bus.publish(KernelEvent("test.event", "kernel"))
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        bus.publish(KernelEvent("test.event", "kernel"))
        assertTrue(invocations.get() == 1)

        release.countDown()
    }

    @Test
    fun `event subscriptions enforce per owner and global quotas`() {
        val limits = KernelRuntimeLimits(
            maxLifecycleCallbacks = 1,
            maxExtensionCallbacks = 2,
            maxExtensionCallbacksPerOwner = 1,
            maxEventSubscriptions = 2,
            maxEventSubscriptionsPerOwner = 1,
            eventDispatchTimeoutMillis = 100
        )
        val supervisor = CallbackSupervisor(BudgetedKernelExecutor(DirectKernelExecutor, limits), NoopKernelLogger)
        val bus = EventBus(NoopKernelLogger, supervisor, 50, KernelMutationGuard()) { }
        val first = ModuleLease(OwnerToken("first", 1))
        val second = ModuleLease(OwnerToken("second", 1))
        val third = ModuleLease(OwnerToken("third", 1))

        bus.subscribe(first, "one") { }
        assertFailsWith<IllegalStateException> { bus.subscribe(first, "two") { } }
        bus.subscribe(second, "two") { }
        assertFailsWith<IllegalStateException> { bus.subscribe(third, "three") { } }
    }

    @Test
    fun `timeout before event callback starts releases invocation lease`() {
        val executorEntered = CountDownLatch(1)
        val releaseExecutor = CountDownLatch(1)
        val callbackInvocations = AtomicInteger(0)
        val limits = KernelRuntimeLimits(
            maxLifecycleCallbacks = 1,
            maxExtensionCallbacks = 1,
            maxExtensionCallbacksPerOwner = 1,
            maxEventSubscriptions = 4,
            maxEventSubscriptionsPerOwner = 2,
            eventDispatchTimeoutMillis = 100
        )
        val blockingExecutor = object : KernelExecutor, KernelRuntimeLimitsProvider {
            override val runtimeLimits: KernelRuntimeLimits = limits

            override fun execute(taskName: String, task: () -> Unit): Unit {
                executorEntered.countDown()
                waitIgnoringInterrupts(releaseExecutor)
                task()
            }
        }
        val supervisor = CallbackSupervisor(blockingExecutor, NoopKernelLogger)
        val bus = EventBus(NoopKernelLogger, supervisor, 30, KernelMutationGuard()) { }
        val owner = startedOwner("blocked")
        bus.subscribe(owner, "blocked.event") { callbackInvocations.incrementAndGet() }

        bus.publish(KernelEvent("blocked.event", "kernel"))
        assertTrue(executorEntered.await(1, TimeUnit.SECONDS))
        assertTrue(owner.quiesce(100))
        assertTrue(callbackInvocations.get() == 0)

        releaseExecutor.countDown()
    }

    @Test
    fun `command handler cannot be reentered after timeout until actual callback termination`() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val invocations = AtomicInteger(0)
        val limits = KernelRuntimeLimits(
            maxLifecycleCallbacks = 1,
            maxExtensionCallbacks = 2,
            maxExtensionCallbacksPerOwner = 1,
            maxEventSubscriptions = 4,
            maxEventSubscriptionsPerOwner = 2,
            eventDispatchTimeoutMillis = 100
        )
        val supervisor = CallbackSupervisor(BudgetedKernelExecutor(DirectKernelExecutor, limits), NoopKernelLogger)
        val bus = CommandBus(supervisor, 40, KernelMutationGuard()) { }
        val owner = startedOwner("commandowner")
        bus.register(owner, "work.hang", replace = false) {
            invocations.incrementAndGet()
            entered.countDown()
            waitIgnoringInterrupts(release)
            CommandResult.success()
        }

        val first = AtomicReference<CommandResult>()
        val caller = Thread { first.set(bus.execute(command("work.hang"))) }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        caller.join(1_000)
        assertFalse(first.get().success)

        val second = bus.execute(command("work.hang"))
        assertFalse(second.success)
        assertTrue(second.error?.message?.contains("already has an invocation in flight") == true)
        assertTrue(invocations.get() == 1)

        release.countDown()
    }

    private fun startedOwner(id: String): ModuleLease = ModuleLease(OwnerToken(id, 1)).also {
        it.activateInvocations()
    }

    private fun waitIgnoringInterrupts(release: CountDownLatch): Unit {
        while (release.count > 0) {
            try {
                release.await(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                // Deliberately keep the callback/executor alive until the test releases it.
            }
        }
    }
}
