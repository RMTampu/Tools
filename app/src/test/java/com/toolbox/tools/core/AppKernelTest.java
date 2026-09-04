package com.toolbox.tools.core;

import com.toolbox.tools.library.LibraryItemType;
import com.toolbox.tools.library.LibraryKey;
import com.toolbox.tools.library.VersionNumber;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class AppKernelTest {
    @Test
    public void defaultKernelPassesTahapThreeVerification() {
        AppKernel kernel = AppKernel.createDefault();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertTrue(result.message(), result.isPass());
        assertEquals(AppState.READY, kernel.state());
        assertTrue(kernel.toolRegistry().contains("foundation"));
        assertTrue(kernel.engineManager().contains("foundation-engine"));
        assertEquals("30", kernel.configStore().get("targetApi", ""));
        assertEquals("arm64", kernel.configStore().get("targetAbi", ""));
        assertEquals("3", kernel.configStore().get("tahap", ""));
        assertNotNull(kernel.projectManager().current());
        assertEquals("project.default", kernel.projectManager().current().projectId());

        assertNotNull(kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.COMPONENT,
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
        ));
        assertNotNull(kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.TEMPLATE,
                        "template.screen.basic",
                        VersionNumber.parse("1.0.0")
                )
        ));
        assertNotNull(kernel.assetStore());
    }

    @Test
    public void recoveryRequiredFailsVerification() {
        AppKernel kernel = AppKernel.createDefault();
        kernel.recoveryManager().markRecoveryRequired();

        VerificationResult result = new VerificationManager().verify(kernel);

        assertFalse(result.isPass());
    }
}
