package com.toolbox.tools.repair;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.AppState;
import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.delivery.PatchTransactionJournal;
import com.toolbox.tools.product.AssetIntegrityVerifier;
import com.toolbox.tools.product.FreezeEngine;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HealthMonitor {
    public HealthReport inspect(AppKernel kernel) {
        List<String> reasons = new ArrayList<>();

        if (kernel == null) {
            reasons.add("KERNEL_MISSING");
            return new HealthReport(
                    HealthState.RECOVERY_REQUIRED,
                    reasons
            );
        }

        if (kernel.state() != AppState.READY) {
            reasons.add("KERNEL_NOT_READY");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            reasons.add("RECOVERY_REQUIRED");
        }
        if (kernel.safeModeController().isSafeMode()) {
            reasons.add("SAFE_MODE_ACTIVE");
        }

        ProjectAccessStatus access =
                kernel.projectManager().accessStatus();
        if (access != ProjectAccessStatus.PROJECT_OK
                && access != ProjectAccessStatus.FOLDER_MISSING) {
            reasons.add("PROJECT_ACCESS_" + access.name());
        }

        ProjectValidationResult projectValidation =
                new ProjectValidator().validate(
                        kernel.projectManager().current()
                );
        if (!projectValidation.isPass()) {
            reasons.add(
                    "PROJECT_SCHEMA_OR_REFERENCE_INVALID:"
                            + projectValidation.message()
            );
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

        PatchTransactionJournal.Phase patchPhase =
                kernel.safePatchManager().journal().phase();
        if (patchPhase != PatchTransactionJournal.Phase.IDLE) {
            reasons.add(
                    "PATCH_TRANSACTION_INCOMPLETE:"
                            + patchPhase.name()
            );
        }

        FreezeEngine.State freezeState =
                kernel.productServices().freeze().state();
        if (freezeState == FreezeEngine.State.RECOVERY_REQUIRED
                || freezeState == FreezeEngine.State.RECOVERY_RUNNING
                || freezeState == FreezeEngine.State.FAILED_SAFE) {
            reasons.add(
                    "FREEZE_RECOVERY_STATE:"
                            + freezeState.name()
            );
        }

        inspectVisibleStorage(kernel, reasons);
        inspectExternalAssets(kernel, reasons);

        if (containsCritical(reasons)) {
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

    private static void inspectVisibleStorage(
            AppKernel kernel,
            List<String> reasons
    ) {
        try {
            kernel.visibleWorkspaceStore().ensureLayout();
            for (VisibleWorkspaceStore.Area area
                    : VisibleWorkspaceStore.Area.values()) {
                kernel.visibleWorkspaceStore().list(area);
            }
        } catch (IOException | RuntimeException error) {
            reasons.add("VISIBLE_STORAGE_UNAVAILABLE");
        }
    }

    private static void inspectExternalAssets(
            AppKernel kernel,
            List<String> reasons
    ) {
        AssetIntegrityVerifier verifier =
                kernel.productServices().assetIntegrity();
        Map<String, String> resources =
                kernel.projectManager().current().resources();

        for (Map.Entry<String, String> entry
                : resources.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("asset.external.")
                    || !key.endsWith(".storage.name")) {
                continue;
            }
            String assetId = key.substring(
                    0,
                    key.length() - ".storage.name".length()
            );
            String area = resources.get(
                    assetId + ".storage.area"
            );
            String sha = resources.get(
                    assetId + ".sha256"
            );
            if (!VisibleWorkspaceStore.Area.ASSETS.folder()
                    .equals(area)
                    || sha == null
                    || !sha.matches("[0-9a-f]{64}")) {
                reasons.add(
                        "EXTERNAL_ASSET_METADATA_INVALID:"
                                + assetId
                );
                continue;
            }
            try {
                if (!verifier.verify(
                        kernel.visibleWorkspaceStore(),
                        VisibleWorkspaceStore.Area.ASSETS,
                        entry.getValue(),
                        sha
                )) {
                    reasons.add(
                            "EXTERNAL_ASSET_INTEGRITY_FAILED:"
                                    + assetId
                    );
                }
            } catch (IOException | RuntimeException error) {
                reasons.add(
                        "EXTERNAL_ASSET_UNAVAILABLE:"
                                + assetId
                );
            }
        }
    }

    private static boolean containsCritical(
            List<String> reasons
    ) {
        for (String reason : reasons) {
            if ("RECOVERY_REQUIRED".equals(reason)
                    || "KERNEL_NOT_READY".equals(reason)
                    || "KERNEL_MISSING".equals(reason)
                    || reason.startsWith(
                            "PATCH_TRANSACTION_INCOMPLETE:"
                    )
                    || reason.startsWith(
                            "FREEZE_RECOVERY_STATE:"
                    )) {
                return true;
            }
        }
        return false;
    }
}
