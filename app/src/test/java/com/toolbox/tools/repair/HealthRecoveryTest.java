package com.toolbox.tools.repair;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.RecoveryCandidate;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public final class HealthRecoveryTest {
    @Test
    public void healthSeparatesHealthyAndRecoveryRequired() {
        AppKernel kernel = AppKernel.createDefault();
        HealthReport healthy = kernel.healthMonitor().inspect(kernel);

        assertEquals(HealthState.HEALTHY, healthy.state());

        kernel.recoveryManager().markRecoveryRequired();
        HealthReport recovery = kernel.healthMonitor().inspect(kernel);

        assertEquals(
                HealthState.RECOVERY_REQUIRED,
                recovery.state()
        );
        assertTrue(recovery.reasons().contains("RECOVERY_REQUIRED"));
    }

    @Test
    public void recoveryRequiresExplicitPreviewAndRestore()
            throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        kernel.projectManager().putResource(
                "screen.recovery",
                "Revision 1"
        );
        kernel.projectManager().save();
        kernel.projectManager().captureFinalRecoverySnapshot();

        kernel.projectManager().putResource(
                "screen.recovery",
                "Revision 2"
        );
        kernel.projectManager().save();

        List<RecoveryCandidate> candidates =
                kernel.recoveryPreviewService().candidates();
        assertFalse(candidates.isEmpty());

        RecoveryCandidate candidate = candidates.get(0);
        long activeRevision =
                kernel.projectManager().current().revision();
        ProjectState preview =
                kernel.recoveryPreviewService().preview(candidate);

        assertNotNull(preview);
        assertEquals(
                activeRevision,
                kernel.projectManager().current().revision()
        );

        ProjectState restored =
                kernel.recoveryPreviewService()
                        .restoreExplicit(candidate);
        assertEquals(
                restored.revision(),
                kernel.projectManager().current().revision()
        );
    }
}
