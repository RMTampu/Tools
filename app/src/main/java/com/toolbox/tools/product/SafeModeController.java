package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.RecoveryManager;
import com.toolbox.tools.core.RuntimeStateStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SafeModeController {
    private static final String KEY_SAFE_MODE = "safe.mode";
    private static final String KEY_QUARANTINE = "safe.quarantine";

    private final ProjectManager projects;
    private final RecoveryManager recovery;
    private final RuntimeStateStore state;

    public SafeModeController(
            ProjectManager projects,
            RecoveryManager recovery
    ) {
        this(projects, recovery, recovery.stateStore());
    }

    public SafeModeController(
            ProjectManager projects,
            RecoveryManager recovery,
            RuntimeStateStore state
    ) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.state = Objects.requireNonNull(state, "state");
    }

    public synchronized void enter() {
        state.put(KEY_SAFE_MODE, "true");
    }

    public synchronized void exitIfHealthy() {
        if (recovery.isRecoveryRequired()) {
            throw new IllegalStateException("pemulihan masih diperlukan");
        }
        state.put(KEY_SAFE_MODE, "false");
    }

    public synchronized boolean isSafeMode() {
        return Boolean.parseBoolean(state.get(KEY_SAFE_MODE))
                || recovery.isRecoveryRequired();
    }

    public synchronized String statusIndonesia() {
        if (recovery.isRecoveryRequired()) {
            String reason = recovery.reason();
            return reason.isEmpty()
                    ? "Pemulihan diperlukan"
                    : "Pemulihan diperlukan • " + reason;
        }
        return isSafeMode() ? "Mode aman aktif" : "Mode normal";
    }

    public synchronized void discardWorkingChanges() throws IOException {
        projects.reloadSaved();
        recovery.clearRecoveryRequired();
    }

    public synchronized void quarantine(String stableId) {
        String id = com.toolbox.tools.core.StableId.require(
                stableId,
                "quarantineId"
        );
        Set<String> values = mutableQuarantine();
        values.add(id);
        persistQuarantine(values);
        enter();
    }

    public synchronized void removeFromQuarantine(String stableId) {
        Set<String> values = mutableQuarantine();
        values.remove(com.toolbox.tools.core.StableId.require(
                stableId,
                "quarantineId"
        ));
        persistQuarantine(values);
    }

    public synchronized Set<String> quarantined() {
        return Collections.unmodifiableSet(mutableQuarantine());
    }

    public synchronized Map<String, String> diagnosticSnapshot() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        out.put("safeMode", Boolean.toString(isSafeMode()));
        out.put(
                "recoveryRequired",
                Boolean.toString(recovery.isRecoveryRequired())
        );
        out.put("recoveryReason", recovery.reason());
        out.put("recoveryOperation", recovery.operation());
        out.put(
                "quarantine",
                android.text.TextUtils.join(",", quarantined())
        );
        out.put(
                "savedRevision",
                Long.toString(projects.savedRevision())
        );
        out.put(
                "workingDirty",
                Boolean.toString(projects.hasUnsavedChanges())
        );
        return Collections.unmodifiableMap(out);
    }

    public synchronized boolean readOnlyInspectionAllowed() {
        return true;
    }

    private Set<String> mutableQuarantine() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String raw = state.get(KEY_QUARANTINE);
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String item : raw.split(",")) {
            if (!item.trim().isEmpty()) out.add(item.trim());
        }
        return out;
    }

    private void persistQuarantine(Set<String> values) {
        if (values.isEmpty()) {
            state.remove(KEY_QUARANTINE);
            return;
        }
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        state.put(KEY_QUARANTINE, android.text.TextUtils.join(",", sorted));
    }
}
