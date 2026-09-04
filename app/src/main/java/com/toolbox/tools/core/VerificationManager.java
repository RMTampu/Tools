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
        if (!"2".equals(kernel.configStore().get("stage", ""))) {
            return VerificationResult.fail("stage 2 configuration missing");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("recovery required");
        }
        WorkspaceSnapshot workspace = kernel.workspaceManager().current();
        if (workspace == null) {
            return VerificationResult.fail("workspace missing");
        }
        if (!"toolbox.default".equals(workspace.workspaceId())) {
            return VerificationResult.fail("workspace identity mismatch");
        }
        if (workspace.schemaVersion() != WorkspaceSnapshot.CURRENT_SCHEMA_VERSION) {
            return VerificationResult.fail("workspace schema mismatch");
        }
        return VerificationResult.pass("stage 2 workspace foundation ready");
    }
}
