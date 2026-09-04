package com.toolbox.tools.core;

public final class VerificationManager {
    public VerificationResult verify(AppKernel kernel) {
        if (kernel == null) {
            return VerificationResult.fail("kernel missing");
        }
        if (kernel.state() != AppState.READY) {
            return VerificationResult.fail("kernel not ready");
        }
        if (!kernel.toolRegistry().contains("foundation")) {
            return VerificationResult.fail("foundation tool missing");
        }
        if (!kernel.engineManager().contains("foundation-engine")) {
            return VerificationResult.fail("foundation engine missing");
        }
        if (!"30".equals(kernel.configStore().get("targetApi", ""))) {
            return VerificationResult.fail("android api target mismatch");
        }
        if (!"arm64".equals(kernel.configStore().get("targetAbi", ""))) {
            return VerificationResult.fail("android abi target mismatch");
        }
        if (!"2".equals(kernel.configStore().get("tahap", ""))) {
            return VerificationResult.fail("tahap 2 configuration missing");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("recovery required");
        }
        ProjectState project = kernel.projectManager().current();
        if (!"project.default".equals(project.projectId())) {
            return VerificationResult.fail("project identity mismatch");
        }
        if (project.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION) {
            return VerificationResult.fail("project schema mismatch");
        }
        if (project.buildModelVersion() != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            return VerificationResult.fail("build model mismatch");
        }
        if (kernel.projectManager().accessStatus() != ProjectAccessStatus.FOLDER_MISSING
                && kernel.projectManager().accessStatus() != ProjectAccessStatus.PROJECT_OK) {
            return VerificationResult.fail("project access invalid");
        }
        return VerificationResult.pass("tahap 2 project store ready");
    }
}
