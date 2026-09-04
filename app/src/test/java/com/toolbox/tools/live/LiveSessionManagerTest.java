package com.toolbox.tools.live;

import com.toolbox.tools.core.DraftRecoveryStore;
import com.toolbox.tools.core.InMemoryProjectStore;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectMigrationRegistry;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.RecoveryManager;
import com.toolbox.tools.core.RecoverySnapshotStore;
import com.toolbox.tools.repair.RepairPhase;
import com.toolbox.tools.repair.RepairSessionManager;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class LiveSessionManagerTest {
    @Test
    public void liveCompareIsReadOnlyAndTerapkanUsesRepairPipeline()
            throws Exception {
        Fixture fixture = fixture();
        fixture.live.open(
                "live.test.apply",
                fixture.target,
                fixture.scan
        );

        long beforeRevision = fixture.project.savedRevision();
        fixture.live.queue(new LiveChange(
                "change.screen.live",
                "screen.live.demo",
                LiveChangeOperation.UPSERT,
                "Live Value"
        ));

        LiveCompareResult compare = fixture.live.compare();
        assertEquals(1, compare.changeCount());
        assertTrue(compare.checksum().matches("[0-9a-f]{64}"));
        assertFalse(
                fixture.project.current()
                        .resources()
                        .containsKey("screen.live.demo")
        );
        assertEquals(beforeRevision, fixture.project.savedRevision());

        LiveApplyResult applied = fixture.live.terapkan();

        assertTrue(applied.isPass());
        assertEquals(LiveSessionState.APPLIED, applied.state());
        assertEquals(RepairPhase.VERIFIED, fixture.repair.phase());
        assertEquals(
                "Live Value",
                fixture.project.current()
                        .resources()
                        .get("screen.live.demo")
        );
        assertTrue(fixture.project.savedRevision() > beforeRevision);

        long afterApply = fixture.project.savedRevision();
        LiveApplyResult repeated = fixture.live.terapkan();
        assertTrue(repeated.isPass());
        assertEquals("NO_CHANGE", repeated.message());
        assertEquals(afterApply, fixture.project.savedRevision());
    }

    @Test
    public void staleBaseRevisionBecomesConflictWithoutOverwrite()
            throws Exception {
        Fixture fixture = fixture();
        fixture.live.open(
                "live.test.conflict",
                fixture.target,
                fixture.scan
        );
        fixture.live.queue(new LiveChange(
                "change.screen.conflict",
                "screen.live.conflict",
                LiveChangeOperation.UPSERT,
                "Queued"
        ));

        fixture.project.putResource(
                "screen.external.change",
                "Concurrent"
        );
        fixture.project.save();
        long concurrentRevision = fixture.project.savedRevision();

        LiveApplyResult result = fixture.live.terapkan();

        assertFalse(result.isPass());
        assertEquals(LiveSessionState.CONFLICT, result.state());
        assertEquals(
                concurrentRevision,
                fixture.project.savedRevision()
        );
        assertFalse(
                fixture.project.current()
                        .resources()
                        .containsKey("screen.live.conflict")
        );
    }

    @Test
    public void selfEditRejectsProtectedAndNonDeclarativeSurfaces()
            throws Exception {
        Fixture fixture = fixture();
        fixture.live.open(
                "live.test.protected",
                fixture.target,
                fixture.scan
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.live.queue(new LiveChange(
                        "change.kernel",
                        "kernel.security.core",
                        LiveChangeOperation.UPSERT,
                        "Tidak Boleh"
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.live.queue(new LiveChange(
                        "change.other",
                        "internal.binary",
                        LiveChangeOperation.UPSERT,
                        "Tidak Boleh"
                ))
        );

        assertFalse(
                fixture.project.current()
                        .resources()
                        .containsKey("kernel.security.core")
        );
    }

    @Test
    public void liveRuntimeGateRejectsUnavailableTarget()
            throws Exception {
        Fixture fixture = fixture();
        TargetDescriptor unavailable = new TargetDescriptor(
                "target.unavailable",
                "Target Tidak Tersedia",
                false,
                false,
                EditDoor.NONE,
                null
        );
        CapabilityScanResult scan =
                new CapabilityScanner().scan(unavailable);

        assertThrows(
                IllegalStateException.class,
                () -> fixture.live.open(
                        "live.test.unavailable",
                        unavailable,
                        scan
                )
        );
    }

    @Test
    public void liveChangeAndHistoryBudgetsAreBounded()
            throws Exception {
        Fixture fixture = fixture();
        fixture.live.open(
                "live.test.budget",
                fixture.target,
                fixture.scan
        );

        for (int i = 0; i < LiveSessionManager.MAX_CHANGES; i++) {
            fixture.live.queue(new LiveChange(
                    "change.budget." + i,
                    "screen.budget." + i,
                    LiveChangeOperation.UPSERT,
                    String.valueOf(i)
            ));
        }

        assertEquals(
                LiveSessionManager.MAX_CHANGES,
                fixture.live.queuedChangeCount()
        );
        assertThrows(
                IllegalStateException.class,
                () -> fixture.live.queue(new LiveChange(
                        "change.budget.extra",
                        "screen.budget.extra",
                        LiveChangeOperation.UPSERT,
                        "extra"
                ))
        );

        fixture.live.close();

        for (int i = 0; i < LiveSessionManager.MAX_HISTORY + 5; i++) {
            fixture.live.open(
                    "live.history." + i,
                    fixture.target,
                    fixture.scan
            );
            fixture.live.queue(new LiveChange(
                    "change.history." + i,
                    "screen.history.live",
                    LiveChangeOperation.UPSERT,
                    "value-" + i
            ));
            assertTrue(fixture.live.terapkan().isPass());
        }

        assertEquals(
                LiveSessionManager.MAX_HISTORY,
                fixture.live.history().size()
        );
    }

    private static Fixture fixture() throws IOException {
        RecoveryManager recovery = new RecoveryManager();
        ProjectManager project = new ProjectManager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore(),
                new RecoverySnapshotStore(),
                recovery,
                new ProjectMigrationRegistry()
        );
        project.bootstrap("project.live");
        project.putResource("screen.base", "Base");
        project.save();

        RepairSessionManager repair =
                new RepairSessionManager(project, recovery);
        TargetDescriptor target = DefaultLiveFactory.selfTarget();
        CapabilityScanResult scan =
                new CapabilityScanner().scan(target);
        LiveSessionManager live = new LiveSessionManager(
                project,
                repair,
                new SelfEditPolicy(),
                target
        );
        return new Fixture(
                project,
                repair,
                target,
                scan,
                live
        );
    }

    private static final class Fixture {
        final ProjectManager project;
        final RepairSessionManager repair;
        final TargetDescriptor target;
        final CapabilityScanResult scan;
        final LiveSessionManager live;

        Fixture(
                ProjectManager project,
                RepairSessionManager repair,
                TargetDescriptor target,
                CapabilityScanResult scan,
                LiveSessionManager live
        ) {
            this.project = project;
            this.repair = repair;
            this.target = target;
            this.scan = scan;
            this.live = live;
        }
    }
}
