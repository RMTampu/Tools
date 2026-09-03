package io.toolbox.stagea;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.Collections;

final class StageATestFixture {
    static final String TOOL_ID = "demo.tool";
    static final String ACTION_ID = "demo.action.execute";
    static final String CAPABILITY_ID = "demo.capability.execute";
    static final String PERMISSION_ID = "demo.permission.use";
    final ProductRegistry registry = new ProductRegistry();
    final MutablePermissionProvider permissions = new MutablePermissionProvider();
    final MutableCapabilityProvider capabilities = new MutableCapabilityProvider();
    final MutableResourceProvider resources = new MutableResourceProvider();
    final MemoryRecoveryStore store = new MemoryRecoveryStore();
    final RecoveryCoordinator recovery = new RecoveryCoordinator(store);
    final ExecutionGuard guard;
    StageATestFixture() {
        registry.publish(bundle());
        permissions.state = StageAContracts.Availability.AVAILABLE;
        capabilities.state = StageAContracts.Availability.AVAILABLE;
        resources.budget = new SafetyContracts.ResourceBudget(100, 100, 10);
        resources.sample = new SafetyContracts.ResourceSample(10, 10, 1);
        recovery.bootstrap();
        guard = new ExecutionGuard(registry, permissions, capabilities, resources, recovery);
    }
    private static Contracts.ToolBundle bundle() {
        Contracts.PermissionRequirement permission = new Contracts.PermissionRequirement(PERMISSION_ID, Contracts.PermissionKind.OPTIONAL, "", "permission.reason", "permission.denied", "permission.unsupported");
        Contracts.CapabilityContract capability = new Contracts.CapabilityContract(CAPABILITY_ID, "1.0.0", "1.0.0", TOOL_ID, "compat.api30", Collections.singletonList(PERMISSION_ID));
        Contracts.ActionContract action = new Contracts.ActionContract(ACTION_ID, "1.0.0", "1.0.0", TOOL_ID, "schema.input", "schema.output", Collections.singletonList(CAPABILITY_ID), Collections.singletonList(PERMISSION_ID), "sync", "direct", "bounded", "cooperative", "idempotent");
        Contracts.ToolContract tool = new Contracts.ToolContract(TOOL_ID, "1.0.0", "1.0.0", Collections.emptyList(), Collections.emptyList(), Collections.singletonList(ACTION_ID), Collections.singletonList(CAPABILITY_ID), Collections.emptyList(), Collections.singletonList(PERMISSION_ID), "entry.demo");
        return new Contracts.ToolBundle(tool, Collections.emptyList(), Collections.singletonList(action), Collections.singletonList(capability), Collections.emptyList(), Collections.singletonList(permission));
    }
    static final class MutablePermissionProvider implements StageAContracts.PermissionStateProvider {
        StageAContracts.Availability state;
        @Override public StageAContracts.Availability permissionState(String permissionId) { return state; }
    }
    static final class MutableCapabilityProvider implements StageAContracts.CapabilityStateProvider {
        StageAContracts.Availability state;
        @Override public StageAContracts.Availability capabilityState(String capabilityId) { return state; }
    }
    static final class MutableResourceProvider implements StageAContracts.ResourcePolicyProvider {
        SafetyContracts.ResourceBudget budget;
        SafetyContracts.ResourceSample sample;
        @Override public SafetyContracts.ResourceBudget budgetFor(String providerToolId) { return budget; }
        @Override public SafetyContracts.ResourceSample sampleFor(String providerToolId) { return sample; }
    }
    static final class MemoryRecoveryStore implements StageAContracts.RecoveryStateStore {
        SafetyContracts.RecoveryState state = SafetyContracts.RecoveryState.NORMAL;
        boolean failLoad;
        boolean failSave;
        int saves;
        @Override public SafetyContracts.RecoveryState load() { if (failLoad) throw new IllegalStateException("load failure"); return state; }
        @Override public void save(SafetyContracts.RecoveryState value) { if (failSave) throw new IllegalStateException("save failure"); state = value; saves++; }
    }
}
