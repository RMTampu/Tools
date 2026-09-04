package com.toolbox.tools.core;

import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class AppKernelTest {
    @Test
    public void defaultKernelPassesTahapFiveVerification() {
        AppKernel kernel = AppKernel.createDefault();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertTrue(result.message(), result.isPass());
        assertEquals(AppState.READY, kernel.state());
        assertEquals("30", kernel.configStore().get("targetApi", ""));
        assertEquals("arm64", kernel.configStore().get("targetAbi", ""));
        assertEquals("5", kernel.configStore().get("tahap", ""));
        assertNotNull(kernel.runtimeEnvironment());
        assertNotNull(kernel.editorEnvironment());
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

        assertEquals(
                "Tambah ke Layar",
                kernel.editorEnvironment()
                        .shell()
                        .edgePanel(VisualCapabilitySet.defaultEditable())
                        .titleIndonesia()
        );
        kernel.editorEnvironment().shell().setMode(EditorMode.PREVIEW);
        assertFalse(
                kernel.editorEnvironment().shell().editorOverlayVisible()
        );
    }

    @Test
    public void recoveryRequiredFailsVerification() {
        AppKernel kernel = AppKernel.createDefault();
        kernel.recoveryManager().markRecoveryRequired();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertFalse(result.isPass());
    }
}
