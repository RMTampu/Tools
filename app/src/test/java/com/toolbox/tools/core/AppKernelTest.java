package com.toolbox.tools.core;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class AppKernelTest {
    @Test
    public void defaultKernelPassesTahapSixVerification() {
        AppKernel kernel = AppKernel.createDefault();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertTrue(result.message(), result.isPass());
        assertEquals(AppState.READY, kernel.state());
        assertEquals("30", kernel.configStore().get("targetApi", ""));
        assertEquals("arm64", kernel.configStore().get("targetAbi", ""));
        assertEquals("6", kernel.configStore().get("tahap", ""));
        assertNotNull(kernel.runtimeEnvironment());
        assertNotNull(kernel.editorEnvironment());
        assertNotNull(kernel.authoringWorkspace());
        assertSame(
                kernel.runtimeEnvironment(),
                kernel.authoringWorkspace().runtime()
        );
        assertEquals(
                "screen.home",
                kernel.runtimeEnvironment().model().startScreenId()
        );
        assertTrue(
                new RuntimeModelValidator()
                        .validate(kernel.runtimeEnvironment())
                        .isEmpty()
        );

        RenderTree tree = new Renderer().materialize(
                kernel.runtimeEnvironment().model().screen("screen.home"),
                kernel.runtimeEnvironment().components()
        );
        assertEquals(1, tree.nodes().size());
        assertTrue(tree.diagnostics().isEmpty());

        for (AuthoringSection section : AuthoringSection.values()) {
            kernel.authoringWorkspace().activate(section);
            assertEquals(
                    section,
                    kernel.authoringWorkspace().activeSection()
            );
        }

        assertFalse(
                kernel.authoringWorkspace()
                        .searchAll("component.button", 20)
                        .isEmpty()
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
