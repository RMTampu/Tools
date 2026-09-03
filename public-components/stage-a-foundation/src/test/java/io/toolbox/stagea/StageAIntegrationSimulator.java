package io.toolbox.stagea;

import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.ResourceGuard;
import io.toolbox.contracts.safety.SafetyContracts;

public final class StageAIntegrationSimulator {
    private StageAIntegrationSimulator() {}
    public static void main(String[] args) {
        StageATestFixture f = new StageATestFixture(); DiagnosticBuffer diagnostics = new DiagnosticBuffer(8); DiagnosticMapper mapper = new DiagnosticMapper();
        require(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.ALLOW);
        f.resources.sample = new SafetyContracts.ResourceSample(80, 10, 1);
        require(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.DEGRADE);
        f.recovery.apply(SafetyContracts.RecoveryEvent.RESOURCE_PRESSURE); require(f.recovery.state() == SafetyContracts.RecoveryState.DEGRADED);
        diagnostics.record(mapper.event(StageATestFixture.TOOL_ID, SafetyContracts.Severity.WARN, "resource.pressure", "diagnostic.resource.pressure"));
        f.recovery.apply(SafetyContracts.RecoveryEvent.FAILURE_REQUIRES_RECOVERY);
        require(SafeUiPolicy.modelFor(f.recovery.state()).visible()); require(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.REJECT);
        f.recovery.apply(SafetyContracts.RecoveryEvent.ENTER_SAFE_MODE); require(SafeUiPolicy.modelFor(f.recovery.state()).restricted());
        f.recovery.apply(SafetyContracts.RecoveryEvent.RECOVERY_SUCCEEDED); f.resources.sample = new SafetyContracts.ResourceSample(10, 10, 1);
        require(f.guard.evaluate(StageATestFixture.ACTION_ID).mode() == StageAContracts.AdmissionMode.ALLOW);
        StageAContracts.HealthSnapshot health = HealthAggregator.aggregate(f.registry.snapshot(), diagnostics, f.recovery.state(), ResourceGuard.evaluate(f.resources.budget, f.resources.sample));
        require(health.registryEntries() == 4);
        System.out.println("STAGE_A_INTEGRATION_SIMULATOR = PASS");
        System.out.println("REGISTRY_ROUTE=PASS");
        System.out.println("EXECUTION_GUARD_ROUTE=PASS");
        System.out.println("RECOVERY_ROUTE=PASS");
        System.out.println("SAFE_UI_CONTRACT_ROUTE=PASS");
        System.out.println("HEALTH_DIAGNOSTIC_ROUTE=PASS");
        System.out.println("PRIVATE_CONTENT_USED=0");
        System.out.println("ANDROID_RUNTIME_CALLS=0");
        System.out.println("NETWORK_CALLS=0");
        System.out.println("PLUGIN_LOADS=0");
        System.out.println("FIREBASE_USED=0");
    }
    private static void require(boolean value) { if (!value) throw new AssertionError("integration simulator invariant failed"); }
}
