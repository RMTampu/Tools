package com.toolbox.tools.core;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.integration.ExternalSnapshot;
import com.toolbox.tools.integration.NormalizationResult;
import com.toolbox.tools.integration.SyncPlan;
import com.toolbox.tools.integration.SyncStatus;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import org.junit.Test;

import static org.junit.Assert.*;

public final class AppKernelTest {
    @Test
    public void defaultKernelPassesTahapSevenVerification() {
        AppKernel kernel = AppKernel.createDefault();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertTrue(result.message(), result.isPass());
        assertEquals(AppState.READY, kernel.state());
        assertEquals("30", kernel.configStore().get("targetApi", ""));
        assertEquals("arm64", kernel.configStore().get("targetAbi", ""));
        assertEquals("7", kernel.configStore().get("tahap", ""));
        assertNotNull(kernel.runtimeEnvironment());
        assertNotNull(kernel.editorEnvironment());
        assertNotNull(kernel.authoringWorkspace());
        assertNotNull(kernel.externalIntegrationManager());

        assertSame(
                kernel.runtimeEnvironment(),
                kernel.authoringWorkspace().runtime()
        );
        assertTrue(
                new RuntimeModelValidator()
                        .validate(kernel.runtimeEnvironment())
                        .isEmpty()
        );

        for (AuthoringSection section : AuthoringSection.values()) {
            kernel.authoringWorkspace().activate(section);
            assertEquals(
                    section,
                    kernel.authoringWorkspace().activeSection()
            );
        }

        ExternalSnapshot external = kernel.externalIntegrationManager()
                .demoSnapshot(1, "cursor.kernel.1");
        NormalizationResult normalized = kernel.externalIntegrationManager()
                .importSnapshot(external);
        assertTrue(normalized.isPass());

        SyncPlan plan = kernel.externalIntegrationManager().planSync(external);
        assertEquals(SyncStatus.CLEAN, plan.status());
        kernel.externalIntegrationManager().applySync(plan);
        assertEquals(
                SyncStatus.NO_CHANGE,
                kernel.externalIntegrationManager()
                        .planSync(external)
                        .status()
        );

        kernel.editorEnvironment().shell().setMode(EditorMode.PREVIEW);
        assertFalse(
                kernel.editorEnvironment().shell().editorOverlayVisible()
        );
        kernel.editorEnvironment().shell().setMode(EditorMode.EDIT);

        assertFalse(
                kernel.editorEnvironment()
                        .shell()
                        .edgePanel(VisualCapabilitySet.defaultEditable())
                        .items()
                        .isEmpty()
        );
    }

    @Test
    public void repeatedVerificationIsIdempotent() {
        AppKernel kernel = AppKernel.createDefault();
        VerificationManager verifier = new VerificationManager();

        assertTrue(verifier.verify(kernel).isPass());
        assertTrue(verifier.verify(kernel).isPass());
    }

    @Test
    public void recoveryRequiredFailsVerification() {
        AppKernel kernel = AppKernel.createDefault();
        kernel.recoveryManager().markRecoveryRequired();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertFalse(result.isPass());
    }
}
