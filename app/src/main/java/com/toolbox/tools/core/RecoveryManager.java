package com.toolbox.tools.core;

public final class RecoveryManager {
    private static final String KEY_REQUIRED = "recovery.required";
    private static final String KEY_REASON = "recovery.reason";
    private static final String KEY_OPERATION = "recovery.operation";

    private final RuntimeStateStore state;

    public RecoveryManager() {
        this(new MemoryRuntimeStateStore());
    }

    public RecoveryManager(RuntimeStateStore state) {
        this.state = java.util.Objects.requireNonNull(state, "state");
    }

    public synchronized void markRecoveryRequired() {
        markRecoveryRequired("UNSPECIFIED", "UNKNOWN");
    }

    public synchronized void markRecoveryRequired(
            String reason,
            String operation
    ) {
        state.put(KEY_REQUIRED, "true");
        state.put(KEY_REASON, normalize(reason, "UNSPECIFIED"));
        state.put(KEY_OPERATION, normalize(operation, "UNKNOWN"));
    }

    public synchronized void clearRecoveryRequired() {
        state.put(KEY_REQUIRED, "false");
        state.remove(KEY_REASON);
        state.remove(KEY_OPERATION);
    }

    public synchronized boolean isRecoveryRequired() {
        String raw = state.get(KEY_REQUIRED);
        if (raw == null || "false".equalsIgnoreCase(raw)) {
            return false;
        }
        // Nilai apa pun selain false dianggap unsafe agar metadata semantic
        // corruption tidak pernah membuka jalur normal secara diam-diam.
        return true;
    }

    public synchronized String reason() {
        String value = state.get(KEY_REASON);
        return value == null ? "" : value;
    }

    public synchronized String operation() {
        String value = state.get(KEY_OPERATION);
        return value == null ? "" : value;
    }

    public RuntimeStateStore stateStore() {
        return state;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() > 160
                ? trimmed.substring(0, 160)
                : trimmed;
    }
}
