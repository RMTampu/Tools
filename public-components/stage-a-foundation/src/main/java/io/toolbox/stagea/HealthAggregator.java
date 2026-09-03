package io.toolbox.stagea;

import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.List;
import java.util.Objects;

public final class HealthAggregator {
    private HealthAggregator() {}
    public static StageAContracts.HealthSnapshot aggregate(ProductRegistry.RegistrySnapshot registry, DiagnosticBuffer diagnostics,
            SafetyContracts.RecoveryState recoveryState, SafetyContracts.GuardDecision guardDecision) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(recoveryState, "recoveryState");
        Objects.requireNonNull(guardDecision, "guardDecision");
        StageAContracts.HealthState health = StageAContracts.HealthState.HEALTHY;
        if (recoveryState == SafetyContracts.RecoveryState.QUARANTINED) health = StageAContracts.HealthState.BLOCKED;
        else if (recoveryState == SafetyContracts.RecoveryState.RECOVERY_REQUIRED
                || recoveryState == SafetyContracts.RecoveryState.SAFE_MODE
                || guardDecision.mode() == SafetyContracts.GuardMode.REJECT) health = StageAContracts.HealthState.UNHEALTHY;
        else if (recoveryState == SafetyContracts.RecoveryState.DEGRADED
                || guardDecision.mode() == SafetyContracts.GuardMode.DEGRADE
                || containsError(diagnostics.snapshot())) health = StageAContracts.HealthState.DEGRADED;
        return new StageAContracts.HealthSnapshot(health, registry.totalEntries(), diagnostics.size(), diagnostics.droppedCount(), recoveryState, guardDecision.mode());
    }
    private static boolean containsError(List<SafetyContracts.DiagnosticEvent> events) {
        for (SafetyContracts.DiagnosticEvent event : events) {
            if (event.severity() == SafetyContracts.Severity.ERROR || event.severity() == SafetyContracts.Severity.FATAL) return true;
        }
        return false;
    }
}
