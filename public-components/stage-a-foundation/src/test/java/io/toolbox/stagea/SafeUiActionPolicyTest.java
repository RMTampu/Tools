package io.toolbox.stagea;

import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.SafetyContracts;

public final class SafeUiActionPolicyTest {
    private SafeUiActionPolicyTest() {}

    public static void main(String[] args) {
        StageAContracts.HealthSnapshot healthy = new StageAContracts.HealthSnapshot(
                StageAContracts.HealthState.HEALTHY,
                3,
                0,
                0,
                SafetyContracts.RecoveryState.NORMAL,
                SafetyContracts.GuardMode.ALLOW
        );
        check(SafeUiActionPolicy.inspection(healthy).contains("health=HEALTHY"));
        check(SafeUiActionPolicy.bootstrapState(SafetyContracts.RecoveryState.SAFE_MODE).contains("SAFE_MODE"));
        check(SafeUiActionPolicy.restrictedModeStatus(SafetyContracts.RecoveryState.SAFE_MODE).contains("Mode aman"));
        check(SafeUiActionPolicy.restrictedModeStatus(SafetyContracts.RecoveryState.NORMAL).contains("tidak mendefinisikan"));

        DiagnosticBuffer buffer = new DiagnosticBuffer(1);
        buffer.record(new SafetyContracts.DiagnosticEvent(
                "event.one", "stage.a.host", SafetyContracts.Severity.WARN,
                "diagnostic.one", "diagnostic.one.message", 1));
        buffer.record(new SafetyContracts.DiagnosticEvent(
                "event.two", "stage.a.host", SafetyContracts.Severity.ERROR,
                "diagnostic.two", "diagnostic.two.message", 2));
        String summary = SafeUiActionPolicy.sanitizedDiagnostics(buffer);
        check(summary.contains("count=1"));
        check(summary.contains("dropped=1"));
        check(summary.contains("diagnostic.two"));
        check(!summary.contains("diagnostic.one"));

        check(!SafeUiActionPolicy.canRestoreKnownGood());
        check(SafeUiActionPolicy.restoreUnavailable().contains("belum memiliki authority"));
        check(SafeUiActionPolicy.canQuarantine(SafetyContracts.RecoveryState.SAFE_MODE));
        check(!SafeUiActionPolicy.canQuarantine(SafetyContracts.RecoveryState.QUARANTINED));
        SafetyContracts.Transition transition = new SafetyContracts.Transition(
                SafetyContracts.RecoveryState.SAFE_MODE,
                SafetyContracts.RecoveryEvent.FATAL_FAILURE,
                SafetyContracts.RecoveryState.QUARANTINED);
        check(SafeUiActionPolicy.quarantineResult(transition).contains("SAFE_MODE -> QUARANTINED"));

        System.out.println("SAFE_UI_ACTION_POLICY_TEST = PASS");
        System.out.println("SAFE_UI_ACTION_POLICY_CASES=13");
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
}
