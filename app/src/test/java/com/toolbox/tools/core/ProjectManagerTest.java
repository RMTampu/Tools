package com.toolbox.tools.core;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ProjectManagerTest {
    @Test
    public void manualSaveAndDraftRecoveryRemainSeparate() throws Exception {
        InMemoryProjectStore store = new InMemoryProjectStore();
        DraftRecoveryStore draft = new DraftRecoveryStore();
        ProjectManager manager = manager(store, draft);

        manager.bootstrap("project.alpha");
        manager.putResource("screen.main", "draft");
        assertTrue(manager.hasUnsavedChanges());
        assertEquals(0, manager.savedRevision());

        manager.writeDraftRecovery();
        assertNotNull(manager.previewDraftRecovery());
        assertEquals(0, manager.savedRevision());

        ProjectState saved = manager.save();
        assertEquals(1, saved.revision());
        assertFalse(manager.hasUnsavedChanges());
        assertNull(manager.previewDraftRecovery());
    }

    @Test
    public void multiResourceActionIsOneUndoGroup() throws Exception {
        ProjectManager manager = manager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore()
        );
        manager.bootstrap("project.alpha");

        Map<String, String> upserts = new LinkedHashMap<>();
        upserts.put("screen.main", "screen");
        upserts.put("asset.logo", "logo");
        manager.applyResourceTransaction(upserts, java.util.Collections.emptySet());

        assertTrue(manager.undo());
        assertTrue(manager.current().resources().isEmpty());
        assertFalse(manager.undo());

        assertTrue(manager.redo());
        assertEquals(2, manager.current().resources().size());
    }

    @Test
    public void unsavedExitHasSaveDiscardCancelSemantics() throws Exception {
        ProjectManager manager = manager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore()
        );
        manager.bootstrap("project.alpha");

        manager.putResource("screen.main", "one");
        assertFalse(manager.handleUnsavedDecision(UnsavedDecision.CANCEL));
        assertTrue(manager.hasUnsavedChanges());

        assertTrue(manager.handleUnsavedDecision(UnsavedDecision.DISCARD));
        assertFalse(manager.hasUnsavedChanges());
        assertTrue(manager.current().resources().isEmpty());

        manager.putResource("screen.main", "two");
        assertTrue(manager.handleUnsavedDecision(UnsavedDecision.SAVE));
        assertEquals(1, manager.savedRevision());
        assertFalse(manager.hasUnsavedChanges());
    }

    @Test
    public void staleWriterIsRejected() throws Exception {
        InMemoryProjectStore store = new InMemoryProjectStore();

        ProjectManager first = manager(store, new DraftRecoveryStore());
        ProjectManager second = manager(store, new DraftRecoveryStore());

        first.bootstrap("project.alpha");
        second.bootstrap("project.alpha");

        first.putResource("screen.main", "one");
        first.save();

        second.putResource("screen.main", "stale");
        assertThrows(StaleWriteException.class, second::save);
        assertTrue(second.hasUnsavedChanges());
    }

    @Test
    public void undoHistoryIsBounded() throws Exception {
        ProjectManager manager = manager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore()
        );
        manager.bootstrap("project.alpha");

        for (int index = 0; index < ProjectManager.MAX_UNDO_GROUPS + 20; index++) {
            manager.putResource("screen.main", Integer.toString(index));
        }

        int count = 0;
        while (manager.undo()) {
            count++;
        }
        assertEquals(ProjectManager.MAX_UNDO_GROUPS, count);
    }

    private static ProjectManager manager(
            ProjectStore store,
            DraftRecoveryStore draft
    ) {
        return new ProjectManager(
                store,
                draft,
                new RecoveryManager(),
                new ProjectMigrationRegistry()
        );
    }
}
