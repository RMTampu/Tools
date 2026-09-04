package com.toolbox.tools.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class AppKernelTest {
    @Test
    public void defaultKernelPassesStageTwoVerification() {
        AppKernel kernel = AppKernel.createDefault();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertTrue(result.message(), result.isPass());
        assertEquals(AppState.READY, kernel.state());
        assertTrue(kernel.toolRegistry().contains("foundation"));
        assertTrue(kernel.engineManager().contains("foundation-engine"));
        assertEquals("30", kernel.configStore().get("targetApi", ""));
        assertEquals("arm64", kernel.configStore().get("targetAbi", ""));
        assertEquals("2", kernel.configStore().get("stage", ""));
        assertNotNull(kernel.workspaceManager().current());
        assertEquals("toolbox.default", kernel.workspaceManager().current().workspaceId());
        assertEquals(WorkspaceSnapshot.CURRENT_SCHEMA_VERSION,
                kernel.workspaceManager().current().schemaVersion());
    }

    @Test
    public void recoveryRequiredFailsVerification() {
        AppKernel kernel = AppKernel.createDefault();
        kernel.recoveryManager().markRecoveryRequired();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertFalse(result.isPass());
    }
}
