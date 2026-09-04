package com.toolbox.tools.build;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.AppState;
import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.core.ProjectLifecycle;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.live.LiveSessionState;
import com.toolbox.tools.repair.HealthState;
import com.toolbox.tools.repair.RepairPhase;
import com.toolbox.tools.product.FullProductVerifier;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BuildValidator {
    private final ProjectValidator projectValidator = new ProjectValidator();

    public BuildValidationResult validate(
            AppKernel kernel,
            boolean requireReadyLifecycle
    ) {
        Objects.requireNonNull(kernel, "kernel");
        List<BuildDiagnostic> diagnostics = new ArrayList<>();

        if (kernel.state() != AppState.READY) {
            diagnostics.add(new BuildDiagnostic(
                    "build.kernel.not_ready",
                    "Kernel belum READY"
            ));
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.recovery.required",
                    "Recovery wajib diselesaikan"
            ));
        }

        ProjectValidationResult project = projectValidator.validate(
                kernel.projectManager().current()
        );
        if (!project.isPass()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.project.invalid",
                    project.message()
            ));
        }
        if (kernel.projectManager().hasUnsavedChanges()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.project.dirty",
                    "Project masih mempunyai perubahan belum tersimpan"
            ));
        }
        ProjectAccessStatus access = kernel.projectManager().accessStatus();
        if (kernel.projectManager().savedRevision() > 0
                && access != ProjectAccessStatus.PROJECT_OK) {
            diagnostics.add(new BuildDiagnostic(
                    "build.project.access",
                    "Project saved tetapi access state tidak PROJECT_OK"
            ));
        }

        if (requireReadyLifecycle
                && kernel.projectManager().current().lifecycle()
                != ProjectLifecycle.READY) {
            diagnostics.add(new BuildDiagnostic(
                    "build.project.lifecycle",
                    "Project belum lifecycle READY"
            ));
        }

        if (!new RuntimeModelValidator()
                .validate(kernel.runtimeEnvironment())
                .isEmpty()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.runtime.invalid",
                    "Runtime model mempunyai diagnostic"
            ));
        }

        HealthState health = kernel.healthMonitor().inspect(kernel).state();
        if (health != HealthState.HEALTHY) {
            diagnostics.add(new BuildDiagnostic(
                    "build.health.not_healthy",
                    "Health bukan HEALTHY"
            ));
        }

        LiveSessionState live = kernel.liveSessionManager().state();
        if (live == LiveSessionState.DIRTY
                || live == LiveSessionState.CONFLICT
                || live == LiveSessionState.FAILED_SAFE) {
            diagnostics.add(new BuildDiagnostic(
                    "build.live.unsafe",
                    "Live session belum aman untuk READY/build"
            ));
        }

        RepairPhase repair = kernel.repairSessionManager().phase();
        if (repair == RepairPhase.STAGED
                || repair == RepairPhase.ACTIVATED
                || repair == RepairPhase.FAILED_SAFE) {
            diagnostics.add(new BuildDiagnostic(
                    "build.repair.pending",
                    "Repair belum terminal aman"
            ));
        }

        if (kernel.libraryManager().components().allReady().isEmpty()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.library.component",
                    "Tidak ada component READY"
            ));
        }
        if (kernel.libraryManager().templates().allReady().isEmpty()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.library.template",
                    "Tidak ada template READY"
            ));
        }

        if (!"30".equals(kernel.configStore().get("targetApi", ""))
                || !"arm64".equals(
                kernel.configStore().get("targetAbi", ""))) {
            diagnostics.add(new BuildDiagnostic(
                    "build.android.target",
                    "Target Android 11/API30 arm64 tidak cocok"
            ));
        }

        if (!"id".equals(kernel.configStore().get("bahasaDefault", ""))) {
            diagnostics.add(new BuildDiagnostic(
                    "build.language.indonesia.required",
                    "Bahasa pengguna default wajib Bahasa Indonesia"
            ));
        }

        FullProductVerifier.Result product =
                new FullProductVerifier().verify(kernel);
        if (!product.isPass()) {
            diagnostics.add(new BuildDiagnostic(
                    "build.product.incomplete",
                    "Produk belum lengkap: "
                            + product.available().size()
                            + "/" + product.requiredCount()
            ));
        }

        return new BuildValidationResult(diagnostics);
    }
}
