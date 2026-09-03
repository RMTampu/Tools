package io.toolbox.stagea;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.Objects;

public final class StageAContracts {
    private StageAContracts() {}

    public static final String STAGE_ID = "toolbox.stage.a";
    public static final String STAGE_VERSION = "1.0.0";

    public enum Availability { AVAILABLE, UNAVAILABLE, UNSUPPORTED }
    public enum AdmissionMode { ALLOW, DEGRADE, REJECT }
    public enum HealthState { HEALTHY, DEGRADED, UNHEALTHY, BLOCKED }

    public interface PermissionStateProvider { Availability permissionState(String permissionId); }
    public interface CapabilityStateProvider { Availability capabilityState(String capabilityId); }
    public interface ResourcePolicyProvider {
        SafetyContracts.ResourceBudget budgetFor(String providerToolId);
        SafetyContracts.ResourceSample sampleFor(String providerToolId);
    }
    public interface RecoveryStateStore {
        SafetyContracts.RecoveryState load();
        void save(SafetyContracts.RecoveryState state);
    }

    public static final class AdmissionDecision {
        private final AdmissionMode mode;
        private final String reasonCode;
        private final String actionId;
        public AdmissionDecision(AdmissionMode mode, String reasonCode, String actionId) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.reasonCode = Contracts.requireStableId(reasonCode, "reasonCode");
            this.actionId = Contracts.requireStableId(actionId, "actionId");
        }
        public AdmissionMode mode() { return mode; }
        public String reasonCode() { return reasonCode; }
        public String actionId() { return actionId; }
        public boolean executable() { return mode != AdmissionMode.REJECT; }
    }

    public static final class SafeUiModel {
        private final boolean visible;
        private final boolean restricted;
        private final String messageKey;
        private final SafetyContracts.RecoveryState recoveryState;
        public SafeUiModel(boolean visible, boolean restricted, String messageKey, SafetyContracts.RecoveryState recoveryState) {
            this.visible = visible;
            this.restricted = restricted;
            this.messageKey = Contracts.requireStableId(messageKey, "messageKey");
            this.recoveryState = Objects.requireNonNull(recoveryState, "recoveryState");
        }
        public boolean visible() { return visible; }
        public boolean restricted() { return restricted; }
        public String messageKey() { return messageKey; }
        public SafetyContracts.RecoveryState recoveryState() { return recoveryState; }
    }

    public static final class HealthSnapshot {
        private final HealthState state;
        private final int registryEntries;
        private final int diagnosticCount;
        private final long droppedDiagnostics;
        private final SafetyContracts.RecoveryState recoveryState;
        private final SafetyContracts.GuardMode guardMode;
        public HealthSnapshot(HealthState state, int registryEntries, int diagnosticCount, long droppedDiagnostics,
                SafetyContracts.RecoveryState recoveryState, SafetyContracts.GuardMode guardMode) {
            this.state = Objects.requireNonNull(state, "state");
            if (registryEntries < 0 || diagnosticCount < 0 || droppedDiagnostics < 0) {
                throw new IllegalArgumentException("health counters must be non-negative");
            }
            this.registryEntries = registryEntries;
            this.diagnosticCount = diagnosticCount;
            this.droppedDiagnostics = droppedDiagnostics;
            this.recoveryState = Objects.requireNonNull(recoveryState, "recoveryState");
            this.guardMode = Objects.requireNonNull(guardMode, "guardMode");
        }
        public HealthState state() { return state; }
        public int registryEntries() { return registryEntries; }
        public int diagnosticCount() { return diagnosticCount; }
        public long droppedDiagnostics() { return droppedDiagnostics; }
        public SafetyContracts.RecoveryState recoveryState() { return recoveryState; }
        public SafetyContracts.GuardMode guardMode() { return guardMode; }
    }

    public static final class StageAException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final String code;
        public StageAException(String code, String message) {
            super(Objects.requireNonNull(message, "message"));
            this.code = Contracts.requireStableId(code, "code");
        }
        public StageAException(String code, String message, Throwable cause) {
            super(Objects.requireNonNull(message, "message"), cause);
            this.code = Contracts.requireStableId(code, "code");
        }
        public String code() { return code; }
    }
}
