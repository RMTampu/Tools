package com.toolbox.tools.core;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectManager {
    public static final int MAX_UNDO_GROUPS = 64;

    private final ProjectStore store;
    private final DraftRecoveryStore draftRecoveryStore;
    private final RecoverySnapshotStore recoverySnapshotStore;
    private final RecoveryManager recoveryManager;
    private final ProjectMigrationRegistry migrationRegistry;
    private final ProjectValidator validator = new ProjectValidator();
    private final IncrementalResourceValidator incrementalValidator = new IncrementalResourceValidator();
    private IncrementalResourceValidator.Result lastIncrementalValidation;
    private final Deque<ProjectChangeSet> undo = new ArrayDeque<>();
    private final Deque<ProjectChangeSet> redo = new ArrayDeque<>();

    private ProjectState current;
    private long savedRevision;
    private boolean dirty;
    private ProjectAccessStatus accessStatus = ProjectAccessStatus.FOLDER_MISSING;

    public ProjectManager(
            ProjectStore store,
            DraftRecoveryStore draftRecoveryStore,
            RecoveryManager recoveryManager,
            ProjectMigrationRegistry migrationRegistry
    ) {
        this(
                store,
                draftRecoveryStore,
                new RecoverySnapshotStore(),
                recoveryManager,
                migrationRegistry
        );
    }

    public ProjectManager(
            ProjectStore store,
            DraftRecoveryStore draftRecoveryStore,
            RecoverySnapshotStore recoverySnapshotStore,
            RecoveryManager recoveryManager,
            ProjectMigrationRegistry migrationRegistry
    ) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.draftRecoveryStore = java.util.Objects.requireNonNull(
                draftRecoveryStore,
                "draftRecoveryStore"
        );
        this.recoverySnapshotStore = java.util.Objects.requireNonNull(
                recoverySnapshotStore,
                "recoverySnapshotStore"
        );
        this.recoveryManager = java.util.Objects.requireNonNull(
                recoveryManager,
                "recoveryManager"
        );
        this.migrationRegistry = java.util.Objects.requireNonNull(
                migrationRegistry,
                "migrationRegistry"
        );
    }

    public synchronized void bootstrap(String projectId) throws IOException {
        ProjectLoadResult load = store.load(projectId);
        accessStatus = load.status();
        undo.clear();
        redo.clear();
        dirty = false;

        if (load.status() == ProjectAccessStatus.FOLDER_MISSING) {
            current = ProjectState.create(projectId);
            savedRevision = 0;
            recoveryManager.clearRecoveryRequired();
            return;
        }

        if (load.state() == null) {
            recoveryManager.markRecoveryRequired();
            throw new IOException("project cannot be loaded: " + load.status());
        }

        current = load.state();
        savedRevision = current.revision();

        if (load.status() == ProjectAccessStatus.PROJECT_OK) {
            recoveryManager.clearRecoveryRequired();
            return;
        }

        recoveryManager.markRecoveryRequired();
    }

    public synchronized ProjectState current() {
        requireStarted();
        return current;
    }

    public RecoveryManager recoveryManager() {
        return recoveryManager;
    }

    public synchronized ProjectAccessStatus accessStatus() {
        return accessStatus;
    }

    public synchronized boolean hasUnsavedChanges() {
        return dirty;
    }

    public synchronized long savedRevision() {
        return savedRevision;
    }

    public synchronized void setLifecycle(ProjectLifecycle next) {
        requireStarted();
        if (next == null) {
            throw new NullPointerException("next lifecycle");
        }
        if (current.lifecycle() == next) {
            return;
        }
        if (current.lifecycle() == ProjectLifecycle.TRASH) {
            throw new IllegalStateException(
                    "TRASH project lifecycle is terminal"
            );
        }
        if (current.lifecycle() == ProjectLifecycle.ARCHIVED
                && next != ProjectLifecycle.TRASH) {
            throw new IllegalStateException(
                    "ARCHIVED project cannot return to active lifecycle"
            );
        }
        current = current.withLifecycle(next);
        dirty = true;
        redo.clear();
    }

    public synchronized void putResource(String resourceId, String payload) {
        Map<String, String> upserts = new LinkedHashMap<>();
        upserts.put(resourceId, payload);
        applyResourceTransaction(upserts, Collections.emptySet());
    }

    public synchronized void removeResource(String resourceId) {
        applyResourceTransaction(
                Collections.emptyMap(),
                Collections.singleton(resourceId)
        );
    }

    public synchronized void applyResourceTransaction(
            Map<String, String> upserts,
            Set<String> deletes
    ) {
        requireStarted();
        if (upserts.isEmpty() && deletes.isEmpty()) {
            return;
        }

        lastIncrementalValidation = incrementalValidator.validate(
                current,
                upserts,
                deletes
        );
        if (!lastIncrementalValidation.isPass()) {
            throw new IllegalArgumentException(
                    lastIncrementalValidation.diagnostic()
            );
        }

        LinkedHashSet<String> touched = new LinkedHashSet<>();
        touched.addAll(upserts.keySet());
        touched.addAll(deletes);

        Map<String, String> beforeValues = new LinkedHashMap<>();
        Set<String> beforeMissing = new LinkedHashSet<>();
        for (String id : touched) {
            StableId.require(id, "resourceId");
            if (current.resources().containsKey(id)) {
                beforeValues.put(id, current.resources().get(id));
            } else {
                beforeMissing.add(id);
            }
        }

        ProjectState next = current;
        for (String id : deletes) {
            next = next.withoutResource(id);
        }
        for (Map.Entry<String, String> entry : upserts.entrySet()) {
            next = next.withResource(entry.getKey(), entry.getValue());
        }

        Map<String, String> afterValues = new LinkedHashMap<>();
        Set<String> afterMissing = new LinkedHashSet<>();
        for (String id : touched) {
            if (next.resources().containsKey(id)) {
                afterValues.put(id, next.resources().get(id));
            } else {
                afterMissing.add(id);
            }
        }

        pushBounded(
                undo,
                new ProjectChangeSet(
                        beforeValues,
                        beforeMissing,
                        afterValues,
                        afterMissing
                )
        );
        redo.clear();
        current = next;
        dirty = true;
    }

    public synchronized boolean undo() {
        requireStarted();
        if (undo.isEmpty()) {
            return false;
        }
        ProjectChangeSet change = undo.removeLast();
        current = change.applyReverse(current);
        pushBounded(redo, change);
        dirty = true;
        return true;
    }

    public synchronized boolean redo() {
        requireStarted();
        if (redo.isEmpty()) {
            return false;
        }
        ProjectChangeSet change = redo.removeLast();
        current = change.applyForward(current);
        pushBounded(undo, change);
        dirty = true;
        return true;
    }

    public synchronized ProjectState save() throws IOException {
        requireStarted();
        ProjectValidationResult validation = validator.validate(current);
        if (!validation.isPass()) {
            throw new IOException("PROJECT_VALIDATION_FAILED:" + validation.message());
        }
        try {
            ProjectState committed = store.commit(current, savedRevision);
            current = committed;
            savedRevision = committed.revision();
            dirty = false;
            accessStatus = ProjectAccessStatus.PROJECT_OK;
            draftRecoveryStore.discard();

            try {
                recoverySnapshotStore.captureLastValid(committed);
                recoveryManager.clearRecoveryRequired();
            } catch (IOException recoveryError) {
                recoveryManager.markRecoveryRequired();
            }
            return committed;
        } catch (IOException error) {
            recoveryManager.markRecoveryRequired();
            throw error;
        }
    }

    public synchronized boolean handleUnsavedDecision(
            UnsavedDecision decision
    ) throws IOException {
        requireStarted();
        if (!dirty) {
            return true;
        }
        switch (decision) {
            case SAVE:
                save();
                return true;
            case DISCARD:
                reloadSaved();
                return true;
            case CANCEL:
            default:
                return false;
        }
    }

    public synchronized void reloadSaved() throws IOException {
        requireStarted();
        if (savedRevision == 0) {
            current = ProjectState.create(current.projectId());
        } else {
            current = store.loadRevision(savedRevision);
        }
        undo.clear();
        redo.clear();
        dirty = false;
        draftRecoveryStore.discard();
    }

    public synchronized void writeDraftRecovery() throws IOException {
        requireStarted();
        if (dirty) {
            draftRecoveryStore.writeDraft(current);
        }
    }

    public synchronized ProjectState previewDraftRecovery() throws IOException {
        return draftRecoveryStore.preview();
    }

    public synchronized void captureFinalRecoverySnapshot() throws IOException {
        requireStarted();
        if (dirty || savedRevision <= 0 || current.revision() != savedRevision) {
            throw new IllegalStateException(
                    "final recovery snapshot requires a clean saved revision"
            );
        }
        recoverySnapshotStore.captureFinal(current);
    }

    public synchronized List<RecoveryCandidate> recoveryCandidates()
            throws IOException {
        List<RecoveryCandidate> out = new ArrayList<>();
        out.addAll(recoverySnapshotStore.candidates());
        out.addAll(store.recoveryCandidates());
        return out;
    }

    public synchronized ProjectState previewRecoveryCandidate(
            RecoveryCandidate candidate
    ) throws IOException {
        requireStarted();
        if (candidate == null) {
            throw new NullPointerException("candidate");
        }

        ProjectState preview;
        switch (candidate.kind()) {
            case FINAL_RECOVERY_SNAPSHOT:
            case LAST_VALID_RECOVERY:
                preview = recoverySnapshotStore.preview(candidate.kind());
                break;
            case LAST_VALID_REVISION:
            case OLDER_REVISION:
                preview = store.loadRevision(candidate.revision());
                break;
            default:
                throw new IllegalArgumentException(
                        "draft recovery is previewed separately"
                );
        }

        if (preview == null || !preview.projectId().equals(current.projectId())) {
            throw new IOException("recovery project identity mismatch");
        }
        return preview;
    }

    public synchronized ProjectState restoreRecoveryCandidate(
            RecoveryCandidate candidate
    ) throws IOException {
        ProjectState preview = previewRecoveryCandidate(candidate);
        ProjectValidationResult validation = validator.validate(preview);
        if (!validation.isPass()) {
            throw new IOException("RECOVERY_VALIDATION_FAILED:" + validation.message());
        }

        try {
            ProjectState recovered;
            switch (candidate.kind()) {
                case FINAL_RECOVERY_SNAPSHOT:
                case LAST_VALID_RECOVERY:
                    recovered = store.recoverState(preview);
                    break;
                case LAST_VALID_REVISION:
                case OLDER_REVISION:
                    recovered = store.recoverRevision(candidate.revision());
                    break;
                default:
                    throw new IllegalArgumentException(
                            "draft recovery cannot be silently restored"
                    );
            }
            adoptRecovered(recovered);
            return recovered;
        } catch (IOException error) {
            recoveryManager.markRecoveryRequired();
            throw error;
        }
    }

    public synchronized ProjectState previewMigration() {
        requireStarted();
        return migrationRegistry.previewMigration(current);
    }

    public synchronized ProjectState applyMigration() throws IOException {
        requireStarted();
        if (current.schemaVersion() == ProjectState.CURRENT_SCHEMA_VERSION) {
            return current;
        }
        ProjectState migrated = migrationRegistry.previewMigration(current);
        ProjectValidationResult validation = validator.validate(migrated);
        if (!validation.isPass()) {
            throw new IOException("MIGRATION_VALIDATION_FAILED:" + validation.message());
        }
        current = migrated;
        dirty = true;
        return save();
    }

    public synchronized ProjectState previewRecovery(long revision)
            throws IOException {
        requireStarted();
        ProjectState candidate = store.loadRevision(revision);
        if (!candidate.projectId().equals(current.projectId())) {
            throw new IOException("recovery project identity mismatch");
        }
        return candidate;
    }

    public synchronized ProjectState restoreExternalState(
            ProjectState candidate
    ) throws IOException {
        requireStarted();
        if (candidate == null) throw new NullPointerException("candidate");
        if (!current.projectId().equals(candidate.projectId())) {
            throw new IOException("external restore project identity mismatch");
        }
        ProjectValidationResult validation = validator.validate(candidate);
        if (!validation.isPass()) {
            throw new IOException(
                    "EXTERNAL_RESTORE_VALIDATION_FAILED:"
                            + validation.message()
            );
        }
        try {
            ProjectState recovered = store.recoverState(candidate);
            adoptRecovered(recovered);
            return recovered;
        } catch (IOException error) {
            recoveryManager.markRecoveryRequired(
                    "EXTERNAL_RESTORE_FAILED",
                    "RESTORE_EXTERNAL"
            );
            throw error;
        }
    }

    public synchronized ProjectState restoreRevision(long revision)
            throws IOException {
        requireStarted();
        ProjectState candidate = previewRecovery(revision);
        ProjectValidationResult validation = validator.validate(candidate);
        if (!validation.isPass()) {
            throw new IOException("RECOVERY_VALIDATION_FAILED:" + validation.message());
        }
        try {
            ProjectState recovered = store.recoverRevision(revision);
            adoptRecovered(recovered);
            return recovered;
        } catch (IOException error) {
            recoveryManager.markRecoveryRequired();
            throw error;
        }
    }

    public synchronized boolean deleteRecoveryCandidate(
            RecoveryCandidate candidate
    ) throws IOException {
        requireStarted();
        if (candidate == null) throw new NullPointerException("candidate");
        if (candidate.kind() != RecoveryCandidate.Kind.OLDER_REVISION) {
            return false;
        }
        return store.deleteRecoveryRevision(candidate.revision());
    }

    public synchronized int deleteAllSafeRecoveryCandidates()
            throws IOException {
        requireStarted();
        int deleted = 0;
        for (RecoveryCandidate candidate : recoveryCandidates()) {
            if (candidate.kind()
                    == RecoveryCandidate.Kind.OLDER_REVISION
                    && store.deleteRecoveryRevision(
                            candidate.revision()
                    )) {
                deleted++;
            }
        }
        return deleted;
    }

    public synchronized IncrementalResourceValidator.Result
            lastIncrementalValidation() {
        return lastIncrementalValidation;
    }

    public synchronized boolean canUndo() {
        return !undo.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redo.isEmpty();
    }

    private void adoptRecovered(ProjectState recovered) throws IOException {
        current = recovered;
        savedRevision = recovered.revision();
        dirty = false;
        undo.clear();
        redo.clear();
        accessStatus = ProjectAccessStatus.PROJECT_OK;
        recoveryManager.clearRecoveryRequired();
        draftRecoveryStore.discard();
        try {
            recoverySnapshotStore.captureLastValid(recovered);
        } catch (IOException recoveryError) {
            recoveryManager.markRecoveryRequired();
        }
    }

    private static void pushBounded(
            Deque<ProjectChangeSet> stack,
            ProjectChangeSet change
    ) {
        if (stack.size() == MAX_UNDO_GROUPS) {
            stack.removeFirst();
        }
        stack.addLast(change);
    }

    private void requireStarted() {
        if (current == null) {
            throw new IllegalStateException("ProjectManager belum di-bootstrap");
        }
    }
}
