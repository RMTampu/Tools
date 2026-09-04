package com.toolbox.tools.core;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class WorkspaceManager {
    public static final int MAX_HISTORY = 64;

    private final StorageGateway storageGateway;
    private final RecoveryManager recoveryManager;
    private final Deque<WorkspaceSnapshot> undo = new ArrayDeque<>();
    private final Deque<WorkspaceSnapshot> redo = new ArrayDeque<>();

    private WorkspaceSnapshot current;
    private long savedRevision = -1;

    public WorkspaceManager(
            StorageGateway storageGateway,
            RecoveryManager recoveryManager
    ) {
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager");
    }

    public synchronized void bootstrap(String workspaceId) throws IOException {
        try {
            current = storageGateway.exists()
                    ? storageGateway.load()
                    : WorkspaceSnapshot.create(workspaceId);
            if (!current.workspaceId().equals(workspaceId)) {
                throw new IOException("workspace identity mismatch");
            }
            savedRevision = current.revision();
            undo.clear();
            redo.clear();
            if (storageGateway.recoveredFromBackup()) {
                recoveryManager.markRecoveryRequired();
            } else {
                recoveryManager.clearRecoveryRequired();
            }
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("workspace bootstrap failed", error);
        }
    }

    public synchronized WorkspaceSnapshot current() {
        return current;
    }

    public synchronized WorkspaceSnapshot put(String key, String value) {
        requireStarted();
        pushUndo(current);
        redo.clear();
        current = current.withValue(key, value, current.revision() + 1);
        return current;
    }

    public synchronized WorkspaceSnapshot remove(String key) {
        requireStarted();
        if (!current.values().containsKey(key)) {
            return current;
        }
        pushUndo(current);
        redo.clear();
        current = current.withoutValue(key, current.revision() + 1);
        return current;
    }

    public synchronized boolean undo() {
        requireStarted();
        if (undo.isEmpty()) {
            return false;
        }
        WorkspaceSnapshot previous = undo.removeLast();
        pushBounded(redo, current);
        current = WorkspaceSnapshot.restore(
                current.workspaceId(),
                current.schemaVersion(),
                current.revision() + 1,
                previous.values()
        );
        return true;
    }

    public synchronized boolean redo() {
        requireStarted();
        if (redo.isEmpty()) {
            return false;
        }
        WorkspaceSnapshot next = redo.removeLast();
        pushUndo(current);
        current = WorkspaceSnapshot.restore(
                current.workspaceId(),
                current.schemaVersion(),
                current.revision() + 1,
                next.values()
        );
        return true;
    }

    public synchronized void save() throws IOException {
        requireStarted();
        try {
            storageGateway.save(current);
            savedRevision = current.revision();
            recoveryManager.clearRecoveryRequired();
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("workspace save failed", error);
        }
    }

    public synchronized void reloadFromStorage() throws IOException {
        requireStarted();
        try {
            WorkspaceSnapshot loaded = storageGateway.load();
            if (!loaded.workspaceId().equals(current.workspaceId())) {
                throw new IOException("workspace identity mismatch");
            }
            current = loaded;
            savedRevision = loaded.revision();
            undo.clear();
            redo.clear();
            if (storageGateway.recoveredFromBackup()) {
                recoveryManager.markRecoveryRequired();
            } else {
                recoveryManager.clearRecoveryRequired();
            }
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("workspace reload failed", error);
        }
    }

    public synchronized boolean canUndo() {
        return !undo.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redo.isEmpty();
    }

    public synchronized boolean hasUnsavedChanges() {
        requireStarted();
        return current.revision() != savedRevision;
    }

    public synchronized long savedRevision() {
        return savedRevision;
    }

    private void pushUndo(WorkspaceSnapshot snapshot) {
        pushBounded(undo, snapshot);
    }

    private static void pushBounded(
            Deque<WorkspaceSnapshot> stack,
            WorkspaceSnapshot snapshot
    ) {
        if (stack.size() == MAX_HISTORY) {
            stack.removeFirst();
        }
        stack.addLast(snapshot);
    }

    private void requireStarted() {
        if (current == null) {
            throw new IllegalStateException("workspace manager not bootstrapped");
        }
    }
}
