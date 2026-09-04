package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.RecoveryManager;
import java.io.IOException;
import java.util.Objects;

public final class SafeModeController {
    private final ProjectManager projects;
    private final RecoveryManager recovery;
    private boolean safeMode;

    public SafeModeController(
            ProjectManager projects,
            RecoveryManager recovery
    ) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    public synchronized void enter() {
        safeMode = true;
    }

    public synchronized void exitIfHealthy() {
        if (recovery.isRecoveryRequired()) {
            throw new IllegalStateException("pemulihan masih diperlukan");
        }
        safeMode = false;
    }

    public synchronized boolean isSafeMode() {
        return safeMode || recovery.isRecoveryRequired();
    }

    public synchronized String statusIndonesia() {
        if (recovery.isRecoveryRequired()) {
            return "Pemulihan diperlukan";
        }
        return isSafeMode() ? "Mode aman aktif" : "Mode normal";
    }

    public synchronized void discardWorkingChanges() throws IOException {
        projects.reloadSaved();
        recovery.clearRecoveryRequired();
    }
}
