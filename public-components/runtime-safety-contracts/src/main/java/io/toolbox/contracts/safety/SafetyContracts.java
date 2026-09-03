package io.toolbox.contracts.safety;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Public, metadata-only safety contracts. This package owns no Android lifecycle,
 * persistence, network, plugin loading, signing, Firebase, or permission-grant authority.
 */
public final class SafetyContracts {
    private SafetyContracts() {}

    public static final int MAX_STABLE_ID_LENGTH = 128;
    public static final int MAX_DIAGNOSTIC_CAPACITY = 256;
    public static final int MAX_BUDGET = 1_000_000;
    public static final int MAX_SAMPLE = 2_000_000;

    private static final Pattern STABLE_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    public enum Severity {
        INFO,
        WARN,
        ERROR,
        FATAL
    }

    public enum GuardMode {
        ALLOW,
        DEGRADE,
        REJECT
    }

    public enum RecoveryState {
        NORMAL,
        DEGRADED,
        RECOVERY_REQUIRED,
        SAFE_MODE,
        QUARANTINED
    }

    public enum RecoveryEvent {
        RESOURCE_PRESSURE,
        RESOURCE_NORMAL,
        FAILURE_REQUIRES_RECOVERY,
        ENTER_SAFE_MODE,
        RECOVERY_SUCCEEDED,
        RECOVERY_FAILED,
        FATAL_FAILURE
    }

    public static String requireStableId(String value, String field) {
        if (value == null) {
            throw new ContractException("CONTRACT_INVALID", field + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ContractException("CONTRACT_INVALID", field + " must not be blank");
        }
        if (normalized.length() > MAX_STABLE_ID_LENGTH) {
            throw new ContractException("RESOURCE_LIMIT", field + " exceeds maximum length " + MAX_STABLE_ID_LENGTH);
        }
        if (!STABLE_ID.matcher(normalized).matches()) {
            throw new ContractException("CONTRACT_INVALID", field + " is not a valid Stable ID");
        }
        return normalized;
    }

    static int requireBudget(int value, String field) {
        if (value < 1 || value > MAX_BUDGET) {
            throw new ContractException("RESOURCE_LIMIT", field + " must be within 1.." + MAX_BUDGET);
        }
        return value;
    }

    static int requireSample(int value, String field) {
        if (value < 0 || value > MAX_SAMPLE) {
            throw new ContractException("RESOURCE_LIMIT", field + " must be within 0.." + MAX_SAMPLE);
        }
        return value;
    }

    public static final class ContractException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String code;

        public ContractException(String code, String message) {
            super(Objects.requireNonNull(message, "message"));
            this.code = requireStableId(code.toLowerCase().replace('_', '.'), "failureCode");
        }

        public String code() {
            return code.toUpperCase().replace('.', '_');
        }
    }

    public static final class DiagnosticEvent {
        private final String eventId;
        private final String sourceId;
        private final Severity severity;
        private final String code;
        private final String messageKey;
        private final long sequence;

        public DiagnosticEvent(
                String eventId,
                String sourceId,
                Severity severity,
                String code,
                String messageKey,
                long sequence
        ) {
            this.eventId = requireStableId(eventId, "eventId");
            this.sourceId = requireStableId(sourceId, "sourceId");
            this.severity = Objects.requireNonNull(severity, "severity");
            this.code = requireStableId(code, "code");
            this.messageKey = requireStableId(messageKey, "messageKey");
            if (sequence < 0) {
                throw new ContractException("CONTRACT_INVALID", "sequence must be non-negative");
            }
            this.sequence = sequence;
        }

        public String eventId() { return eventId; }
        public String sourceId() { return sourceId; }
        public Severity severity() { return severity; }
        public String code() { return code; }
        public String messageKey() { return messageKey; }
        public long sequence() { return sequence; }
    }

    public static final class ResourceBudget {
        private final int memoryUnits;
        private final int workUnits;
        private final int concurrentOperations;

        public ResourceBudget(int memoryUnits, int workUnits, int concurrentOperations) {
            this.memoryUnits = requireBudget(memoryUnits, "memoryUnits");
            this.workUnits = requireBudget(workUnits, "workUnits");
            this.concurrentOperations = requireBudget(concurrentOperations, "concurrentOperations");
        }

        public int memoryUnits() { return memoryUnits; }
        public int workUnits() { return workUnits; }
        public int concurrentOperations() { return concurrentOperations; }
    }

    public static final class ResourceSample {
        private final int memoryUnits;
        private final int workUnits;
        private final int concurrentOperations;

        public ResourceSample(int memoryUnits, int workUnits, int concurrentOperations) {
            this.memoryUnits = requireSample(memoryUnits, "memoryUnits");
            this.workUnits = requireSample(workUnits, "workUnits");
            this.concurrentOperations = requireSample(concurrentOperations, "concurrentOperations");
        }

        public int memoryUnits() { return memoryUnits; }
        public int workUnits() { return workUnits; }
        public int concurrentOperations() { return concurrentOperations; }
    }

    public static final class GuardDecision {
        private final GuardMode mode;
        private final String reasonCode;

        public GuardDecision(GuardMode mode, String reasonCode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.reasonCode = requireStableId(reasonCode, "reasonCode");
        }

        public GuardMode mode() { return mode; }
        public String reasonCode() { return reasonCode; }
    }

    public static final class Transition {
        private final RecoveryState previous;
        private final RecoveryEvent event;
        private final RecoveryState next;

        public Transition(RecoveryState previous, RecoveryEvent event, RecoveryState next) {
            this.previous = Objects.requireNonNull(previous, "previous");
            this.event = Objects.requireNonNull(event, "event");
            this.next = Objects.requireNonNull(next, "next");
        }

        public RecoveryState previous() { return previous; }
        public RecoveryEvent event() { return event; }
        public RecoveryState next() { return next; }
        public boolean changed() { return previous != next; }
    }
}
