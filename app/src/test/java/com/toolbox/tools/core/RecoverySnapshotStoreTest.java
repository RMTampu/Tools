package com.toolbox.tools.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class RecoverySnapshotStoreTest {
    @Test
    public void recoveryPriorityIsFinalThenLastValidThenRevisionHistory()
            throws Exception {
        InMemoryProjectStore store = new InMemoryProjectStore();
        RecoverySnapshotStore snapshots = new RecoverySnapshotStore();
        ProjectManager manager = new ProjectManager(
                store,
                new DraftRecoveryStore(),
                snapshots,
                new RecoveryManager(),
                new ProjectMigrationRegistry()
        );

        manager.bootstrap("project.alpha");
        manager.putResource("screen.main", "one");
        manager.save();
        manager.captureFinalRecoverySnapshot();

        manager.putResource("screen.main", "two");
        manager.save();

        List<RecoveryCandidate> candidates = manager.recoveryCandidates();

        assertEquals(
                RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT,
                candidates.get(0).kind()
        );
        assertEquals(
                RecoveryCandidate.Kind.LAST_VALID_RECOVERY,
                candidates.get(1).kind()
        );
        assertEquals(
                RecoveryCandidate.Kind.LAST_VALID_REVISION,
                candidates.get(2).kind()
        );
        assertEquals(
                "one",
                manager.previewRecoveryCandidate(candidates.get(0))
                        .resources().get("screen.main")
        );
    }

    @Test
    public void explicitFinalSnapshotRestorePublishesNewRevision()
            throws Exception {
        InMemoryProjectStore store = new InMemoryProjectStore();
        RecoverySnapshotStore snapshots = new RecoverySnapshotStore();
        ProjectManager manager = new ProjectManager(
                store,
                new DraftRecoveryStore(),
                snapshots,
                new RecoveryManager(),
                new ProjectMigrationRegistry()
        );

        manager.bootstrap("project.alpha");
        manager.putResource("screen.main", "one");
        manager.save();
        manager.captureFinalRecoverySnapshot();

        manager.putResource("screen.main", "two");
        manager.save();

        RecoveryCandidate finalCandidate =
                manager.recoveryCandidates().get(0);
        ProjectState restored =
                manager.restoreRecoveryCandidate(finalCandidate);

        assertEquals(3, restored.revision());
        assertEquals("one", restored.resources().get("screen.main"));
        assertEquals(3, manager.savedRevision());
    }

    @Test
    public void finalSnapshotCannotCaptureDirtyWorkingState()
            throws Exception {
        ProjectManager manager = new ProjectManager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore(),
                new RecoverySnapshotStore(),
                new RecoveryManager(),
                new ProjectMigrationRegistry()
        );
        manager.bootstrap("project.alpha");
        manager.putResource("screen.main", "one");

        assertThrows(
                IllegalStateException.class,
                manager::captureFinalRecoverySnapshot
        );
    }
}
