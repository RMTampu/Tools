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
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("recovery required");
        }
        return VerificationResult.pass("stage 1 foundation ready");
    }
}
