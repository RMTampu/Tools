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
        if (kernel.productEngines() == null
                || !kernel.productEngines().semuaSiap()) {
            reasons.add("ENGINE_CAPABILITY_UNAVAILABLE");
        }
        if (kernel.libraryManager().components().allReady().isEmpty()) {
            reasons.add("COMPONENT_REGISTRY_EMPTY");
        }
        if (kernel.libraryManager().assets().allReady().isEmpty()) {
            reasons.add("ASSET_REGISTRY_EMPTY");
        }
        if (kernel.libraryManager().templates().allReady().isEmpty()) {
            reasons.add("TEMPLATE_REGISTRY_EMPTY");
        }
        if (!kernel.runtimeEnvironment()
                .navigation()
                .validateRoutes()
                .isEmpty()) {
            reasons.add("NAVIGATION_DIAGNOSTIC");
        }
        if (kernel.runtimeEnvironment()
                .model()
                .bindings()
                .isEmpty()) {
            reasons.add("BINDING_MODEL_EMPTY");
        }
        if (kernel.projectManager().current().schemaVersion()
                != com.toolbox.tools.core.ProjectState.CURRENT_SCHEMA_VERSION) {
            reasons.add("SCHEMA_VERSION_MISMATCH");
        }
        if (!kernel.productServices().inventory().complete()) {
            reasons.add("REPOSITORY_INVENTORY_INCOMPLETE");
        }
        if (!kernel.productServices().resources().invariantPass()) {
            reasons.add("RESOURCE_INVARIANT_FAILED");
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
