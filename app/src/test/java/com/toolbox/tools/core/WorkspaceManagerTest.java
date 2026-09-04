package com.toolbox.tools.core;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class WorkspaceManagerTest {
    @Test
    public void saveUndoRedoAndRevisionRemainDeterministic() throws Exception {
        InMemoryStorageGateway storage = new InMemoryStorageGateway();
        RecoveryManager recovery = new RecoveryManager();
        WorkspaceManager manager = new WorkspaceManager(storage, recovery);

        manager.bootstrap("toolbox.test");
        assertEquals(0, manager.current().revision());
        assertFalse(manager.hasUnsavedChanges());

        manager.put("title", "Alpha");
        assertEquals(1, manager.current().revision());
        assertTrue(manager.hasUnsavedChanges());

        manager.save();
        assertEquals(1, manager.savedRevision());
        assertFalse(manager.hasUnsavedChanges());

        manager.put("title", "Beta");
        assertEquals(2, manager.current().revision());
        assertTrue(manager.undo());
        assertEquals(3, manager.current().revision());
        assertEquals("Alpha", manager.current().value("title", ""));
        assertTrue(manager.redo());
        assertEquals(4, manager.current().revision());
        assertEquals("Beta", manager.current().value("title", ""));

        manager.save();
        assertFalse(manager.hasUnsavedChanges());
        assertFalse(recovery.isRecoveryRequired());
    }

    @Test
    public void persistedSnapshotLoadsIntoNewManager() throws Exception {
        InMemoryStorageGateway storage = new InMemoryStorageGateway();

        WorkspaceManager first = new WorkspaceManager(storage, new RecoveryManager());
        first.bootstrap("toolbox.test");
        first.put("mode", "visual");
        first.save();

        WorkspaceManager second = new WorkspaceManager(storage, new RecoveryManager());
        second.bootstrap("toolbox.test");

        assertEquals(first.current(), second.current());
        assertEquals("visual", second.current().value("mode", ""));
    }

    @Test
    public void failedSaveMarksRecoveryRequired() throws Exception {
        RecoveryManager recovery = new RecoveryManager();
        StorageGateway broken = new StorageGateway() {
            @Override
            public boolean exists() {
                return false;
            }

            @Override
            public WorkspaceSnapshot load() throws IOException {
                throw new IOException("not available");
            }

            @Override
            public void save(WorkspaceSnapshot snapshot) throws IOException {
                throw new IOException("disk failure");
            }
        };

        WorkspaceManager manager = new WorkspaceManager(broken, recovery);
        manager.bootstrap("toolbox.test");
        manager.put("title", "Alpha");

        assertThrows(IOException.class, manager::save);
        assertTrue(recovery.isRecoveryRequired());
    }

    @Test
    public void historyIsBounded() throws Exception {
        WorkspaceManager manager = new WorkspaceManager(
                new InMemoryStorageGateway(),
                new RecoveryManager()
        );
        manager.bootstrap("toolbox.test");

        for (int index = 0; index < WorkspaceManager.MAX_HISTORY + 20; index++) {
            manager.put("value", Integer.toString(index));
        }

        int undoCount = 0;
        while (manager.undo()) {
            undoCount++;
        }
        assertEquals(WorkspaceManager.MAX_HISTORY, undoCount);
    }
}
