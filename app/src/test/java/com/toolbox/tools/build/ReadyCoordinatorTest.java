package com.toolbox.tools.build;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.ProjectLifecycle;
import com.toolbox.tools.live.CapabilityScanResult;
import com.toolbox.tools.live.LiveChange;
import com.toolbox.tools.live.LiveChangeOperation;
import com.toolbox.tools.repair.RepairPlan;
import com.toolbox.tools.repair.RepairValidationResult;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public final class ReadyCoordinatorTest {
    @Test
    public void readyPreviewIsReadOnlyAndPublishIsRevisioned()
            throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        com.toolbox.tools.core.ProjectState before =
                kernel.projectManager().current();
        long savedBefore =
                kernel.projectManager().savedRevision();

        BuildValidationResult preview =
                kernel.readyCoordinator().preview();

        assertTrue(preview.message(), preview.isPass());
        assertEquals(before, kernel.projectManager().current());
        assertEquals(
                savedBefore,
                kernel.projectManager().savedRevision()
        );

        com.toolbox.tools.core.ProjectState ready =
                kernel.readyCoordinator().publishReady();

        assertEquals(ProjectLifecycle.READY, ready.lifecycle());
        assertTrue(ready.revision() >= 2);
        assertEquals(
                ready.revision(),
                kernel.projectManager().savedRevision()
        );
        assertFalse(
                kernel.projectManager().recoveryCandidates().isEmpty()
        );
        assertTrue(
                kernel.buildValidator()
                        .validate(kernel, true)
                        .isPass()
        );
    }

    @Test
    public void dirtyProjectBlocksReady() {
        AppKernel kernel = AppKernel.createDefault();
        kernel.projectManager().putResource(
                "screen.ready.dirty",
                "Dirty"
        );

        BuildValidationResult result =
                kernel.readyCoordinator().preview();

        assertFalse(result.isPass());
        assertTrue(result.message().contains(
                "build.project.dirty"
        ));
        assertThrows(
                IllegalStateException.class,
                () -> kernel.readyCoordinator().publishReady()
        );
    }

    @Test
    public void dirtyLiveSessionBlocksReady() throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        kernel.projectManager().save();

        CapabilityScanResult scan = kernel.capabilityScanner()
                .scan(kernel.selfTargetDescriptor());
        kernel.liveSessionManager().open(
                "live.ready.block",
                kernel.selfTargetDescriptor(),
                scan
        );
        kernel.liveSessionManager().queue(new LiveChange(
                "change.ready.block",
                "screen.ready.block",
                LiveChangeOperation.UPSERT,
                "Queued"
        ));

        BuildValidationResult result =
                kernel.readyCoordinator().preview();

        assertFalse(result.isPass());
        assertTrue(result.message().contains(
                "build.live.unsafe"
        ));
    }

    @Test
    public void stagedRepairBlocksReady() throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        kernel.projectManager().save();

        RepairPlan plan = new RepairPlan(
                "repair.ready.block",
                kernel.projectManager().current().projectId(),
                kernel.projectManager().savedRevision(),
                Collections.singletonMap(
                        "screen.ready.repair",
                        "Repair"
                ),
                Collections.emptySet()
        );
        RepairValidationResult staged =
                kernel.repairSessionManager().stage(plan);
        assertTrue(staged.isPass());

        BuildValidationResult result =
                kernel.readyCoordinator().preview();

        assertFalse(result.isPass());
        assertTrue(result.message().contains(
                "build.repair.pending"
        ));
    }
}
