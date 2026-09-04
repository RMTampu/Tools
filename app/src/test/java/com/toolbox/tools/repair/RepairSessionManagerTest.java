package com.toolbox.tools.repair;

import com.toolbox.tools.core.DraftRecoveryStore;
import com.toolbox.tools.core.InMemoryProjectStore;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectMigrationRegistry;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.RecoveryManager;
import com.toolbox.tools.core.RecoverySnapshotStore;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;

public final class RepairSessionManagerTest {
    @Test
    public void stagingIsReadOnlyAndChecksumIsDeterministic()
            throws Exception {
        Fixture fixture = fixture();
        ProjectState before = fixture.manager.current();

        RepairPlan first = plan(
                fixture,
                "repair.stage",
                "screen.demo",
                "Baru"
        );
        RepairPlan second = plan(
                fixture,
                "repair.stage",
                "screen.demo",
                "Baru"
        );

        assertEquals(first.checksum(), second.checksum());
        assertTrue(first.checksum().matches("[0-9a-f]{64}"));

        RepairValidationResult result =
                fixture.repair.stage(first);

        assertTrue(result.isPass());
        assertEquals(RepairPhase.STAGED, fixture.repair.phase());
        assertEquals(before, fixture.manager.current());
        assertEquals(
                before.revision(),
                fixture.manager.savedRevision()
        );
    }

    @Test
    public void activateCreatesRecoveryPointAndVerifyClosesRepair()
            throws Exception {
        Fixture fixture = fixture();
        long before = fixture.manager.savedRevision();
        RepairPlan plan = plan(
                fixture,
                "repair.activate",
                "screen.demo",
                "Diperbaiki"
        );

        assertTrue(fixture.repair.stage(plan).isPass());
        ProjectState activated = fixture.repair.activate();

        assertEquals(RepairPhase.ACTIVATED, fixture.repair.phase());
        assertEquals(before, fixture.repair.preActivationRevision());
        assertTrue(activated.revision() > before);
        assertEquals(
                "Diperbaiki",
                activated.resources().get("screen.demo")
        );

        assertTrue(fixture.repair.verifyOrRollback());
        assertEquals(RepairPhase.VERIFIED, fixture.repair.phase());
        assertFalse(fixture.recovery.isRecoveryRequired());
        assertFalse(
                fixture.manager.recoveryCandidates().isEmpty()
        );
    }

    @Test
    public void verificationFailureRollsBackAndRollbackIsIdempotent()
            throws Exception {
        Fixture fixture = fixture();
        long before = fixture.manager.savedRevision();
        String original = fixture.manager.current()
                .resources()
                .get("screen.demo");

        RepairPlan plan = plan(
                fixture,
                "repair.rollback",
                "screen.demo",
                "Expected"
        );
        assertTrue(fixture.repair.stage(plan).isPass());
        fixture.repair.activate();

        fixture.manager.putResource(
                "screen.demo",
                "Tampered Before Verify"
        );

        assertFalse(fixture.repair.verifyOrRollback());
        assertEquals(RepairPhase.ROLLED_BACK, fixture.repair.phase());
        assertEquals(before, fixture.manager.savedRevision());
        assertEquals(
                original,
                fixture.manager.current()
                        .resources()
                        .get("screen.demo")
        );

        ProjectState same = fixture.repair.rollback();
        assertEquals(before, same.revision());
        assertEquals(RepairPhase.ROLLED_BACK, fixture.repair.phase());
    }

    @Test
    public void staleRevisionAndProtectedCoreFailClosed()
            throws Exception {
        Fixture fixture = fixture();

        RepairPlan stale = new RepairPlan(
                "repair.stale",
                fixture.manager.current().projectId(),
                fixture.manager.savedRevision() + 1,
                Collections.singletonMap(
                        "screen.demo",
                        "X"
                ),
                Collections.emptySet()
        );
        assertFalse(fixture.repair.stage(stale).isPass());
        assertEquals(RepairPhase.FAILED_SAFE, fixture.repair.phase());

        Fixture protectedFixture = fixture();
        RepairPlan protectedPlan = new RepairPlan(
                "repair.protected",
                protectedFixture.manager.current().projectId(),
                protectedFixture.manager.savedRevision(),
                Collections.singletonMap(
                        "recovery.core",
                        "Tidak boleh"
                ),
                Collections.emptySet()
        );
        RepairValidationResult result =
                protectedFixture.repair.stage(protectedPlan);

        assertFalse(result.isPass());
        assertEquals(
                "repair.protected.core",
                result.diagnostics().get(0).code()
        );
        assertFalse(
                protectedFixture.manager.current()
                        .resources()
                        .containsKey("recovery.core")
        );
    }

    @Test
    public void repairHistoryIsBounded() throws Exception {
        Fixture fixture = fixture();

        for (int i = 0; i < RepairSessionManager.MAX_HISTORY + 8; i++) {
            RepairPlan plan = new RepairPlan(
                    "repair.history." + i,
                    fixture.manager.current().projectId(),
                    fixture.manager.savedRevision(),
                    Collections.singletonMap(
                            "screen.history",
                            String.valueOf(i)
                    ),
                    Collections.emptySet()
            );
            assertTrue(fixture.repair.stage(plan).isPass());
        }

        assertEquals(
                RepairSessionManager.MAX_HISTORY,
                fixture.repair.history().size()
        );
    }

    private static RepairPlan plan(
            Fixture fixture,
            String id,
            String resource,
            String payload
    ) {
        return new RepairPlan(
                id,
                fixture.manager.current().projectId(),
                fixture.manager.savedRevision(),
                Collections.singletonMap(resource, payload),
                Collections.emptySet()
        );
    }

    private static Fixture fixture() throws IOException {
        RecoveryManager recovery = new RecoveryManager();
        ProjectManager manager = new ProjectManager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore(),
                new RecoverySnapshotStore(),
                recovery,
                new ProjectMigrationRegistry()
        );
        manager.bootstrap("project.repair");
        manager.putResource("screen.demo", "Awal");
        manager.save();
        return new Fixture(
                manager,
                recovery,
                new RepairSessionManager(manager, recovery)
        );
    }

    private static final class Fixture {
        final ProjectManager manager;
        final RecoveryManager recovery;
        final RepairSessionManager repair;

        Fixture(
                ProjectManager manager,
                RecoveryManager recovery,
                RepairSessionManager repair
        ) {
            this.manager = manager;
            this.recovery = recovery;
            this.repair = repair;
        }
    }
}
