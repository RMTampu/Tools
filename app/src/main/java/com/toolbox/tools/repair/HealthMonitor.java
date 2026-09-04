package com.toolbox.tools.repair;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.AppState;
import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import java.util.ArrayList;
import java.util.List;

public final class HealthMonitor {
    public HealthReport inspect(AppKernel kernel) {
        List<String> reasons = new ArrayList<>();

        if (kernel.state() != AppState.READY) {
            reasons.add("KERNEL_NOT_READY");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            reasons.add("RECOVERY_REQUIRED");
        }

        ProjectAccessStatus access =
                kernel.projectManager().accessStatus();
        if (access != ProjectAccessStatus.PROJECT_OK
                && access != ProjectAccessStatus.FOLDER_MISSING) {
            reasons.add("PROJECT_ACCESS_" + access.name());
        }

        if (!new RuntimeModelValidator()
                .validate(kernel.runtimeEnvironment())
                .isEmpty()) {
            reasons.add("RUNTIME_MODEL_DIAGNOSTIC");
        }
        if (kernel.editorEnvironment() == null) {
            reasons.add("EDITOR_UNAVAILABLE");
        }
        if (kernel.externalIntegrationManager() == null) {
            reasons.add("EXTERNAL_INTEGRATION_UNAVAILABLE");
        }

        if (reasons.contains("RECOVERY_REQUIRED")
                || reasons.contains("KERNEL_NOT_READY")) {
            return new HealthReport(
                    HealthState.RECOVERY_REQUIRED,
                    reasons
            );
        }
        if (!reasons.isEmpty()) {
            return new HealthReport(
                    HealthState.DEGRADED,
                    reasons
            );
        }
        return new HealthReport(
                HealthState.HEALTHY,
                reasons
        );
    }
}
