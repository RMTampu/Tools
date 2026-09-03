package io.toolbox.stagea;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.ResourceGuard;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.Objects;
import java.util.Optional;

public final class ExecutionGuard {
    private final ProductRegistry registry;
    private final StageAContracts.PermissionStateProvider permissions;
    private final StageAContracts.CapabilityStateProvider capabilities;
    private final StageAContracts.ResourcePolicyProvider resources;
    private final RecoveryCoordinator recovery;

    public ExecutionGuard(ProductRegistry registry, StageAContracts.PermissionStateProvider permissions,
            StageAContracts.CapabilityStateProvider capabilities, StageAContracts.ResourcePolicyProvider resources,
            RecoveryCoordinator recovery) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    public StageAContracts.AdmissionDecision evaluate(String actionId) {
        String stableActionId;
        try {
            stableActionId = Contracts.requireStableId(actionId, "actionId");
        } catch (RuntimeException failure) {
            return reject("admission.invalid.action", "invalid.action");
        }
        try {
            SafetyContracts.RecoveryState recoveryState = recovery.state();
            if (recoveryState == SafetyContracts.RecoveryState.RECOVERY_REQUIRED
                    || recoveryState == SafetyContracts.RecoveryState.SAFE_MODE
                    || recoveryState == SafetyContracts.RecoveryState.QUARANTINED) {
                return reject("admission.recovery.block", stableActionId);
            }
            Optional<Contracts.ActionContract> actionOptional = registry.action(stableActionId);
            if (!actionOptional.isPresent()) return reject("admission.action.unavailable", stableActionId);
            Contracts.ActionContract action = actionOptional.get();
            for (String capabilityId : action.capabilityRequirements()) {
                if (!registry.capability(capabilityId).isPresent()) return reject("admission.capability.unregistered", stableActionId);
                StageAContracts.Availability availability = capabilities.capabilityState(capabilityId);
                if (availability == null || availability == StageAContracts.Availability.UNAVAILABLE) return reject("admission.capability.unavailable", stableActionId);
                if (availability == StageAContracts.Availability.UNSUPPORTED) return reject("admission.capability.unsupported", stableActionId);
            }
            for (String permissionId : action.permissionNeeds()) {
                if (!registry.permission(permissionId).isPresent()) return reject("admission.permission.unregistered", stableActionId);
                StageAContracts.Availability availability = permissions.permissionState(permissionId);
                if (availability == null || availability == StageAContracts.Availability.UNAVAILABLE) return reject("admission.permission.denied", stableActionId);
                if (availability == StageAContracts.Availability.UNSUPPORTED) return reject("admission.permission.unsupported", stableActionId);
            }
            SafetyContracts.ResourceBudget budget = resources.budgetFor(action.providerToolId());
            SafetyContracts.ResourceSample sample = resources.sampleFor(action.providerToolId());
            if (budget == null || sample == null) return reject("admission.resource.policy.missing", stableActionId);
            SafetyContracts.GuardDecision resourceDecision = ResourceGuard.evaluate(budget, sample);
            if (resourceDecision.mode() == SafetyContracts.GuardMode.REJECT) return reject("admission.resource.rejected", stableActionId);
            if (resourceDecision.mode() == SafetyContracts.GuardMode.DEGRADE) {
                return new StageAContracts.AdmissionDecision(StageAContracts.AdmissionMode.DEGRADE, "admission.resource.degraded", stableActionId);
            }
            return new StageAContracts.AdmissionDecision(StageAContracts.AdmissionMode.ALLOW, "admission.allowed", stableActionId);
        } catch (RuntimeException failure) {
            return reject("admission.internal.failure", stableActionId);
        }
    }

    private static StageAContracts.AdmissionDecision reject(String reasonCode, String actionId) {
        return new StageAContracts.AdmissionDecision(StageAContracts.AdmissionMode.REJECT, reasonCode, actionId);
    }
}
