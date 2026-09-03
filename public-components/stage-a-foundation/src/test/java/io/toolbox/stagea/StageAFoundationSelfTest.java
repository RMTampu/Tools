package io.toolbox.stagea;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.ResourceGuard;
import io.toolbox.contracts.safety.SafetyContracts;

public final class StageAFoundationSelfTest {
    private static int cases;
    private StageAFoundationSelfTest() {}
    public static void main(String[] args) {
        testAllow(); testPermissionDenied(); testPermissionUnsupported(); testCapabilityUnavailable(); testCapabilityUnsupported();
        testResourceDegrade(); testResourceReject(); testRecoveryBlocksExecution(); testRecoverySuccessUnblocks(); testFatalQuarantineBlocks();
        testPersistenceFailureFailClosed(); testBootstrapFailureFailClosed(); testSafeUiPolicy(); testDiagnosticMapping(); testHealthBlocked();
        testHealthDegradedByDiagnostic(); testInvalidActionFailsClosed(); testMissingResourcePolicyFailsClosed();
        System.out.println("PUBLIC_STAGE_A_FOUNDATION_TESTS = PASS");
        System.out.println("SELF_TEST_CASES=" + cases);
    }
    private static void testAllow() { StageATestFixture f = new StageATestFixture(); check(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.ALLOW); }
    private static void testPermissionDenied() { StageATestFixture f = new StageATestFixture(); f.permissions.state = StageAContracts.Availability.UNAVAILABLE; check(f.guard.evaluate(StageATestFixture.ACTION_ID).reasonCode().equals("admission.permission.denied")); }
    private static void testPermissionUnsupported() { StageATestFixture f = new StageATestFixture(); f.permissions.state = StageAContracts.Availability.UNSUPPORTED; check(f.guard.evaluate(StageATestFixture.ACTION_ID).reasonCode().equals("admission.permission.unsupported")); }
    private static void testCapabilityUnavailable() { StageATestFixture f = new StageATestFixture(); f.capabilities.state = StageAContracts.Availability.UNAVAILABLE; check(f.guard.evaluate(StageATestFixture.ACTION_ID).reasonCode().equals("admission.capability.unavailable")); }
    private static void testCapabilityUnsupported() { StageATestFixture f = new StageATestFixture(); f.capabilities.state = StageAContracts.Availability.UNSUPPORTED; check(f.guard.evaluate(StageATestFixture.ACTION_ID).reasonCode().equals("admission.capability.unsupported")); }
    private static void testResourceDegrade() { StageATestFixture f = new StageATestFixture(); f.resources.sample = new SafetyContracts.ResourceSample(80, 10, 1); check(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.DEGRADE); }
    private static void testResourceReject() { StageATestFixture f = new StageATestFixture(); f.resources.sample = new SafetyContracts.ResourceSample(101, 10, 1); check(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.REJECT); }
    private static void testRecoveryBlocksExecution() { StageATestFixture f = new StageATestFixture(); f.recovery.apply(SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY); check(f.guard.evaluate(StageATestFixture.ACTION_ID).reasonCode().equals("admission.recovery.block")); }
    private static void testRecoverySuccessUnblocks() { StageATestFixture f = new StageATestFixture(); f.recovery.apply(SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY); f.recovery.apply(SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED); check(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.ALLOW); }
    private static void testFatalQuarantineBlocks() { StageATestFixture f = new StageATestFixture(); f.recovery.apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE); check(f.recovery.state() == SafetyContracts.RecoveryState.QUARANTINED); check(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.REJECT); }
    private static void testPersistenceFailureFailClosed() {
        StageATestFixture f = new StageATestFixture(); f.store.failSave = true; boolean failed = false;
        try { f.recovery.apply(SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY); }
        catch (StageAContracts.StageAException expected) { failed = expected.code().equals("recovery.state.persist.failed"); }
        check(failed && f.recovery.state() == SafetyContracts.RecoveryState.QUARANTINED);
    }
    private static void testBootstrapFailureFailClosed() { StageATestFixture.MemoryRecoveryStore store = new StageATestFixture.MemoryRecoveryStore(); store.failLoad = true; RecoveryCoordinator coordinator = new RecoveryCoordinator(store); check(coordinator.bootstrap() == SafetyContracts.RecoveryState.QUARANTINED); }
    private static void testSafeUiPolicy() { check(!SafeUiPolicy.modelFor(SafetyContracts.RecoveryState.NORMAL).visible()); check(SafeUiPolicy.modelFor(SafetyContracts.RecoveryState.RECOVERY_REQUIRED).restricted()); check(SafeUiPolicy.modelFor(SafetyContracts.RecoveryState.SAFE_MODE).visible()); check(SafeUiPolicy.modelFor(SafetyContracts.RecoveryState.QUARANTINED).restricted()); }
    private static void testDiagnosticMapping() {
        DiagnosticMapper mapper = new DiagnosticMapper();
        SafetyContracts.DiagnosticEvent event = mapper.fromFailure("demo.tool", new Contracts.ContractException("DEPENDENCY_MISSING", "x"));
        check(event.code().equals("dependency.missing")); check(event.sequence() == 0L);
    }
    private static void testHealthBlocked() {
        StageATestFixture f = new StageATestFixture(); DiagnosticBuffer buffer = new DiagnosticBuffer(4);
        StageAContracts.HealthSnapshot health = HealthAggregator.aggregate(f.registry.snapshot(), buffer, SafetyContracts.RecoveryState.QUARANTINED, ResourceGuard.evaluate(f.resources.budget, f.resources.sample));
        check(health.state() == StageAContracts.HealthState.BLOCKED);
    }
    private static void testHealthDegradedByDiagnostic() {
        StageATestFixture f = new StageATestFixture(); DiagnosticBuffer buffer = new DiagnosticBuffer(4); DiagnosticMapper mapper = new DiagnosticMapper();
        buffer.record(mapper.event("demo.tool", SafetyContracts.Severity.ERROR, "runtime.failure", "diagnostic.failure"));
        StageAContracts.HealthSnapshot health = HealthAggregator.aggregate(f.registry.snapshot(), buffer, SafetyContracts.RecoveryState.NORMAL, ResourceGuard.evaluate(f.resources.budget, f.resources.sample));
        check(health.state() == StageAContracts.HealthState.DEGRADED);
    }
    private static void testInvalidActionFailsClosed() { StageATestFixture f = new StageATestFixture(); check(f.guard.evaluate("INVALID ACTION").mode() == StageAContracts.AdmissionMode.REJECT); }
    private static void testMissingResourcePolicyFailsClosed() { StageATestFixture f = new StageATestFixture(); f.resources.sample = null; check(f.guard.evaluate(StageATestFixture.ACTION_ID).reasonCode().equals("admission.resource.policy.missing")); }
    private static void check(boolean condition) { cases++; if (!condition) throw new AssertionError("case " + cases + " failed"); }
}
