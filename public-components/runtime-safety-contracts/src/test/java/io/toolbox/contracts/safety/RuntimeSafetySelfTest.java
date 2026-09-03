package io.toolbox.contracts.safety;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeSafetySelfTest {
    private RuntimeSafetySelfTest() {}

    public static void main(String[] args) throws Exception {
        int cases = 0;
        stableIdValidation(); cases++;
        validDiagnosticAndBuffer(); cases++;
        bufferDropOldestIsBounded(); cases++;
        immutableSnapshot(); cases++;
        guardAllowsNormalLoad(); cases++;
        guardDegradesAtPressure(); cases++;
        guardRejectsExceededBudget(); cases++;
        invalidBudgetFailsClosed(); cases++;
        invalidSampleFailsClosed(); cases++;
        normalToDegradedAndBack(); cases++;
        deterministicRecoveryFlow(); cases++;
        illegalTransitionFailsClosedWithoutMutation(); cases++;
        fatalFailureQuarantines(); cases++;
        quarantineIsTerminal(); cases++;
        concurrentDiagnosticRecordingRemainsBounded(); cases++;
        concurrentFatalFailureEndsQuarantined(); cases++;

        System.out.println("PUBLIC_RUNTIME_SAFETY_TESTS = PASS");
        System.out.println("SELF_TEST_CASES=" + cases);
    }

    private static void stableIdValidation() {
        check("runtime.safety".equals(SafetyContracts.requireStableId(" runtime.safety ", "id")), "trimmed id");
        expectCode("CONTRACT_INVALID", () -> SafetyContracts.requireStableId("Runtime.Safety", "id"));
    }

    private static void validDiagnosticAndBuffer() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(2);
        buffer.record(event("diagnostic.one", 1));
        check(buffer.size() == 1, "size after record");
        check(buffer.snapshot().get(0).sequence() == 1, "sequence preserved");
    }

    private static void bufferDropOldestIsBounded() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(2);
        buffer.record(event("diagnostic.one", 1));
        buffer.record(event("diagnostic.two", 2));
        buffer.record(event("diagnostic.three", 3));
        List<SafetyContracts.DiagnosticEvent> snapshot = buffer.snapshot();
        check(snapshot.size() == 2, "bounded size");
        check(snapshot.get(0).sequence() == 2 && snapshot.get(1).sequence() == 3, "drop oldest ordering");
        check(buffer.droppedCount() == 1, "drop count");
    }

    private static void immutableSnapshot() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(1);
        buffer.record(event("diagnostic.one", 1));
        boolean failed = false;
        try {
            buffer.snapshot().clear();
        } catch (UnsupportedOperationException expected) {
            failed = true;
        }
        check(failed, "snapshot immutable");
    }

    private static void guardAllowsNormalLoad() {
        SafetyContracts.GuardDecision decision = ResourceGuard.evaluate(
                new SafetyContracts.ResourceBudget(100, 100, 10),
                new SafetyContracts.ResourceSample(79, 20, 1)
        );
        check(decision.mode() == SafetyContracts.GuardMode.ALLOW, "allow normal load");
    }

    private static void guardDegradesAtPressure() {
        SafetyContracts.GuardDecision decision = ResourceGuard.evaluate(
                new SafetyContracts.ResourceBudget(100, 100, 10),
                new SafetyContracts.ResourceSample(80, 20, 1)
        );
        check(decision.mode() == SafetyContracts.GuardMode.DEGRADE, "degrade at threshold");
    }

    private static void guardRejectsExceededBudget() {
        SafetyContracts.GuardDecision decision = ResourceGuard.evaluate(
                new SafetyContracts.ResourceBudget(100, 100, 10),
                new SafetyContracts.ResourceSample(101, 20, 1)
        );
        check(decision.mode() == SafetyContracts.GuardMode.REJECT, "reject exceeded budget");
    }

    private static void invalidBudgetFailsClosed() {
        expectCode("RESOURCE_LIMIT", () -> new SafetyContracts.ResourceBudget(0, 1, 1));
    }

    private static void invalidSampleFailsClosed() {
        expectCode("RESOURCE_LIMIT", () -> new SafetyContracts.ResourceSample(-1, 0, 0));
    }

    private static void normalToDegradedAndBack() {
        RecoveryMachine machine = new RecoveryMachine();
        machine.apply(SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE);
        check(machine.state() == SafetyContracts.RecoveryState.DEGRADED, "degraded");
        machine.apply(SafetyContracts.RecoveryEvent.RESOURCE_NORMAL);
        check(machine.state() == SafetyContracts.RecoveryState.NORMAL, "normal restored");
    }

    private static void deterministicRecoveryFlow() {
        RecoveryMachine machine = new RecoveryMachine();
        machine.apply(SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY);
        check(machine.state() == SafetyContracts.RecoveryState.RECOVERY_REQUIRED, "recovery required");
        machine.apply(SafetyContracts.RecoveryEvent.ENTER_SAFE_MODE);
        check(machine.state() == SafetyContracts.RecoveryState.SAFE_MODE, "safe mode");
        machine.apply(SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED);
        check(machine.state() == SafetyContracts.RecoveryState.NORMAL, "recovered normal");
    }

    private static void illegalTransitionFailsClosedWithoutMutation() {
        RecoveryMachine machine = new RecoveryMachine();
        expectCode("ILLEGAL_TRANSITION", () -> machine.apply(SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED));
        check(machine.state() == SafetyContracts.RecoveryState.NORMAL, "illegal transition did not mutate");
    }

    private static void fatalFailureQuarantines() {
        RecoveryMachine machine = new RecoveryMachine();
        machine.apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE);
        check(machine.state() == SafetyContracts.RecoveryState.QUARANTINED, "fatal quarantine");
    }

    private static void quarantineIsTerminal() {
        RecoveryMachine machine = new RecoveryMachine(SafetyContracts.RecoveryState.QUARANTINED);
        for (SafetyContracts.RecoveryEvent event : SafetyContracts.RecoveryEvent.values()) {
            machine.apply(event);
            check(machine.state() == SafetyContracts.RecoveryState.QUARANTINED, "quarantine terminal " + event);
        }
    }

    private static void concurrentDiagnosticRecordingRemainsBounded() throws Exception {
        final int workers = 32;
        DiagnosticBuffer buffer = new DiagnosticBuffer(8);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                await(go);
                buffer.record(event("diagnostic.concurrent." + index, index));
            });
            threads[i].start();
        }
        ready.await();
        go.countDown();
        for (Thread thread : threads) thread.join();
        check(buffer.size() == 8, "concurrent bounded size");
        check(buffer.droppedCount() == workers - 8, "concurrent drop count");
    }

    private static void concurrentFatalFailureEndsQuarantined() throws Exception {
        RecoveryMachine machine = new RecoveryMachine();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread pressure = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    SafetyContracts.RecoveryState state = machine.state();
                    if (state == SafetyContracts.RecoveryState.QUARANTINED) return;
                    try {
                        machine.apply(SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE);
                    } catch (SafetyContracts.ContractException ignored) {
                        // A concurrent state change may make this event illegal; state must remain valid.
                    }
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });
        Thread fatal = new Thread(() -> {
            try {
                machine.apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE);
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });
        pressure.start();
        fatal.start();
        pressure.join();
        fatal.join();
        if (error.get() != null) throw new AssertionError(error.get());
        check(machine.state() == SafetyContracts.RecoveryState.QUARANTINED, "fatal wins to terminal state");
    }

    private static SafetyContracts.DiagnosticEvent event(String id, long sequence) {
        return new SafetyContracts.DiagnosticEvent(
                id,
                "runtime.safety.test",
                SafetyContracts.Severity.WARN,
                "diagnostic.test",
                "diagnostic.test.message",
                sequence
        );
    }

    private static void expectCode(String expected, ThrowingRunnable action) {
        try {
            action.run();
            throw new AssertionError("expected failure code " + expected);
        } catch (SafetyContracts.ContractException error) {
            check(expected.equals(error.code()), "expected code " + expected + " actual=" + error.code());
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
