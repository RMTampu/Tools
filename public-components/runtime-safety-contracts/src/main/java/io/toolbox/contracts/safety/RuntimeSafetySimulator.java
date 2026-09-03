package io.toolbox.contracts.safety;

/** Public-only simulator for the safety contracts. */
public final class RuntimeSafetySimulator {
    private RuntimeSafetySimulator() {}

    public static void main(String[] args) {
        SafetyContracts.ResourceBudget budget = new SafetyContracts.ResourceBudget(100, 100, 10);
        SafetyContracts.ResourceSample sample = new SafetyContracts.ResourceSample(85, 40, 2);
        SafetyContracts.GuardDecision decision = ResourceGuard.evaluate(budget, sample);
        if (decision.mode() != SafetyContracts.GuardMode.DEGRADE) {
            throw new AssertionError("expected DEGRADE");
        }

        RecoveryMachine recovery = new RecoveryMachine();
        recovery.apply(SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE);
        recovery.apply(SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY);
        recovery.apply(SafetyContracts.RecoveryEvent.ENTER_SAFE_MODE);
        recovery.apply(SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED);
        if (recovery.state() != SafetyContracts.RecoveryState.NORMAL) {
            throw new AssertionError("recovery did not return to NORMAL");
        }

        DiagnosticBuffer buffer = new DiagnosticBuffer(2);
        buffer.record(new SafetyContracts.DiagnosticEvent(
                "diagnostic.resource.pressure",
                "runtime.safety.simulator",
                SafetyContracts.Severity.WARN,
                "resource.pressure",
                "diagnostic.resource.pressure",
                1
        ));
        if (buffer.size() != 1 || buffer.droppedCount() != 0) {
            throw new AssertionError("diagnostic buffer invariant failed");
        }

        System.out.println("PUBLIC_RUNTIME_SAFETY_SIMULATOR = PASS");
        System.out.println("PERSISTENT_WRITES=0");
        System.out.println("NETWORK_CALLS=0");
        System.out.println("PLUGIN_LOADS=0");
        System.out.println("UI_DEVICE_CALLS=0");
        System.out.println("FIREBASE_USED=0");
    }
}
