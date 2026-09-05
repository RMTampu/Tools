package com.toolbox.tools.product;

import com.toolbox.tools.core.MemoryRuntimeStateStore;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.RecoveryManager;
import com.toolbox.tools.core.RuntimeStateStore;

import java.io.IOException;
import java.util.Objects;

public final class FreezeEngine {
    public enum SaveMode {
        NORMAL,
        CHECKPOINT,
        RECOVERY
    }

    public enum State {
        NORMAL,
        CREATING_SNAPSHOT,
        FROZEN,
        COMMITTING,
        RESTORING,
        THAWING,
        VERIFYING,
        RECOVERY_REQUIRED,
        RECOVERY_RUNNING,
        FAILED_SAFE
    }

    private static final String KEY_STATE = "freeze.state";
    private static final String KEY_MODE = "freeze.save.mode";
    private static final String KEY_FROZEN = "freeze.frozen.revision";
    private static final String KEY_RECOVERY_A = "freeze.recovery.a";
    private static final String KEY_RECOVERY_B = "freeze.recovery.b";
    private static final String KEY_WORKING = "freeze.last.working";
    private static final String KEY_JOURNAL_OPERATION = "freeze.journal.operation";
    private static final String KEY_JOURNAL_PHASE = "freeze.journal.phase";

    private final ProjectManager projects;
    private final RuntimeStateStore persistent;
    private final RecoveryManager recovery;
    private final VisibleArtifactManager visibleArtifacts;

    private State state = State.NORMAL;
    private long frozenRevision;
    private long recoveryARevision;
    private long recoveryBRevision;
    private long lastWorkingRevision;
    private SaveMode saveMode = SaveMode.NORMAL;

    public FreezeEngine(ProjectManager projects) {
        this(
                projects,
                new MemoryRuntimeStateStore(),
                new RecoveryManager(),
                null
        );
    }

    public FreezeEngine(
            ProjectManager projects,
            RuntimeStateStore persistent,
            RecoveryManager recovery
    ) {
        this(projects, persistent, recovery, null);
    }

    public FreezeEngine(
            ProjectManager projects,
            RuntimeStateStore persistent,
            RecoveryManager recovery,
            VisibleArtifactManager visibleArtifacts
    ) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.persistent = Objects.requireNonNull(persistent, "persistent");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.visibleArtifacts = visibleArtifacts;
        loadPersisted();
    }

    public synchronized State state() { return state; }
    public synchronized long frozenRevision() { return frozenRevision; }
    public synchronized long recoveryARevision() { return recoveryARevision; }
    public synchronized long recoveryBRevision() { return recoveryBRevision; }
    public synchronized long lastWorkingRevision() { return lastWorkingRevision; }
    public synchronized SaveMode saveMode() { return saveMode; }

    public synchronized boolean hasFrozenBase() {
        return state == State.FROZEN && frozenRevision > 0;
    }

    public synchronized void bootstrap() throws IOException {
        loadPersisted();
        if (isInterrupted(state) || hasJournal()) {
            recoverInterruptedOperation();
            return;
        }
        if (state == State.FROZEN) {
            if (frozenRevision <= 0) {
                failSafe("FROZEN_WITHOUT_BASE", "BOOTSTRAP");
                return;
            }
            state = State.VERIFYING;
            persistState();
            try {
                projects.previewRecovery(frozenRevision);
                state = State.FROZEN;
                persistState();
                recovery.clearRecoveryRequired();
            } catch (IOException | RuntimeException error) {
                failSafe("FROZEN_BASE_INVALID", "BOOTSTRAP");
                throw error;
            }
        }
    }

    public synchronized void freeze() throws IOException {
        ensureState(State.NORMAL);
        begin("FREEZE", State.CREATING_SNAPSHOT, "SAVE_WORKING");
        try {
            if (projects.hasUnsavedChanges() || projects.savedRevision() <= 0) {
                projects.save();
            }
            journal("CAPTURE_RECOVERY");
            projects.captureFinalRecoverySnapshot();
            mirrorSnapshot("frozen-base", projects.current());
            recoveryBRevision = recoveryARevision;
            recoveryARevision = projects.savedRevision();
            frozenRevision = projects.savedRevision();
            lastWorkingRevision = frozenRevision;
            saveMode = SaveMode.CHECKPOINT;
            state = State.FROZEN;
            finish();
        } catch (IOException | RuntimeException error) {
            failSafe("FREEZE_FAILED", "FREEZE");
            throw error;
        }
    }

    public synchronized ProjectState recover() throws IOException {
        ensureState(State.FROZEN);
        begin("RECOVER", State.RESTORING, "RESTORE_FROZEN_BASE");
        try {
            lastWorkingRevision = projects.savedRevision();
            ProjectState restored = projects.restoreRevision(frozenRevision);
            journal("VERIFY_RECOVERY");
            state = State.VERIFYING;
            persistState();
            projects.previewRecovery(restored.revision());
            projects.captureFinalRecoverySnapshot();
            mirrorSnapshot("recovery", restored);
            saveMode = SaveMode.RECOVERY;
            state = State.FROZEN;
            finish();
            return restored;
        } catch (IOException | RuntimeException error) {
            state = State.RECOVERY_REQUIRED;
            persistState();
            recovery.markRecoveryRequired("FREEZE_RECOVER_FAILED", "RECOVER");
            throw error;
        }
    }

    public synchronized ProjectState commit() throws IOException {
        ensureState(State.FROZEN);
        begin("COMMIT", State.COMMITTING, "SAVE_OVERLAY");
        long previousFrozen = frozenRevision;
        try {
            ProjectState committed = projects.hasUnsavedChanges()
                    ? projects.save()
                    : projects.current();
            journal("VERIFY_COMMIT");
            state = State.VERIFYING;
            persistState();
            projects.previewRecovery(committed.revision());
            projects.captureFinalRecoverySnapshot();
            mirrorSnapshot("commit", committed);
            recoveryBRevision = recoveryARevision;
            recoveryARevision = previousFrozen;
            lastWorkingRevision = committed.revision();
            frozenRevision = committed.revision();
            saveMode = SaveMode.CHECKPOINT;
            state = State.FROZEN;
            finish();
            return committed;
        } catch (IOException | RuntimeException error) {
            frozenRevision = previousFrozen;
            state = State.RECOVERY_REQUIRED;
            persistState();
            recovery.markRecoveryRequired("FREEZE_COMMIT_FAILED", "COMMIT");
            throw error;
        }
    }

    public synchronized void thaw() {
        ensureState(State.FROZEN);
        beginUnchecked("THAW", State.THAWING, "DROP_OVERLAY");
        frozenRevision = 0;
        lastWorkingRevision = projects.savedRevision();
        saveMode = SaveMode.NORMAL;
        state = State.NORMAL;
        finish();
    }

    private void recoverInterruptedOperation() throws IOException {
        String operation = value(KEY_JOURNAL_OPERATION, "UNKNOWN");
        state = State.RECOVERY_RUNNING;
        persistState();
        recovery.markRecoveryRequired(
                "INTERRUPTED_FREEZE_OPERATION",
                operation
        );
        if (frozenRevision <= 0) {
            failSafe("NO_VALID_FROZEN_BASE", operation);
            return;
        }
        try {
            ProjectState restored = projects.restoreRevision(frozenRevision);
            projects.previewRecovery(restored.revision());
            projects.captureFinalRecoverySnapshot();
            mirrorSnapshot("bootstrap-recovery", restored);
            saveMode = SaveMode.RECOVERY;
            state = State.FROZEN;
            finish();
            recovery.clearRecoveryRequired();
        } catch (IOException | RuntimeException error) {
            failSafe("RECOVERY_RUNNING_FAILED", operation);
            throw error;
        }
    }

    private void mirrorSnapshot(
            String label,
            ProjectState state
    ) throws IOException {
        if (visibleArtifacts == null) return;
        visibleArtifacts.snapshot(label, state);
    }

    private void begin(
            String operation,
            State next,
            String phase
    ) {
        beginUnchecked(operation, next, phase);
    }

    private void beginUnchecked(
            String operation,
            State next,
            String phase
    ) {
        persistent.put(KEY_JOURNAL_OPERATION, operation);
        persistent.put(KEY_JOURNAL_PHASE, phase);
        state = next;
        persistState();
    }

    private void journal(String phase) {
        persistent.put(KEY_JOURNAL_PHASE, phase);
        persistState();
    }

    private void finish() {
        persistent.remove(KEY_JOURNAL_OPERATION);
        persistent.remove(KEY_JOURNAL_PHASE);
        persistState();
        recovery.clearRecoveryRequired();
    }

    private void failSafe(String reason, String operation) {
        state = State.FAILED_SAFE;
        persistState();
        recovery.markRecoveryRequired(reason, operation);
    }

    private void loadPersisted() {
        String rawState = persistent.get(KEY_STATE);
        String rawMode = persistent.get(KEY_MODE);
        String rawFrozen = persistent.get(KEY_FROZEN);
        String rawRecoveryA = persistent.get(KEY_RECOVERY_A);
        String rawRecoveryB = persistent.get(KEY_RECOVERY_B);
        String rawWorking = persistent.get(KEY_WORKING);

        State parsedState = parseState(rawState);
        SaveMode parsedMode = parseMode(rawMode);
        Long parsedFrozen = parseLongStrict(rawFrozen);
        Long parsedRecoveryA = parseLongStrict(rawRecoveryA);
        Long parsedRecoveryB = parseLongStrict(rawRecoveryB);
        Long parsedWorking = parseLongStrict(rawWorking);

        boolean invalid =
                (rawState != null && parsedState == null)
                || (rawMode != null && parsedMode == null)
                || parsedFrozen == null
                || parsedRecoveryA == null
                || parsedRecoveryB == null
                || parsedWorking == null;

        state = parsedState == null ? State.NORMAL : parsedState;
        saveMode = parsedMode == null
                ? SaveMode.NORMAL
                : parsedMode;
        frozenRevision = parsedFrozen == null ? 0 : parsedFrozen;
        recoveryARevision =
                parsedRecoveryA == null ? 0 : parsedRecoveryA;
        recoveryBRevision =
                parsedRecoveryB == null ? 0 : parsedRecoveryB;
        lastWorkingRevision =
                parsedWorking == null ? 0 : parsedWorking;

        if (!invalid) {
            invalid = (state == State.NORMAL
                    && (frozenRevision != 0
                        || saveMode != SaveMode.NORMAL))
                    || (state == State.FROZEN
                        && frozenRevision <= 0);
        }

        if (invalid) {
            state = State.FAILED_SAFE;
            saveMode = SaveMode.NORMAL;
            recovery.markRecoveryRequired(
                    "FREEZE_METADATA_INVALID",
                    "BOOTSTRAP"
            );
            persistState();
        }
    }

    private void persistState() {
        persistent.put(KEY_STATE, state.name());
        persistent.put(KEY_MODE, saveMode.name());
        persistent.put(KEY_FROZEN, Long.toString(frozenRevision));
        persistent.put(KEY_RECOVERY_A, Long.toString(recoveryARevision));
        persistent.put(KEY_RECOVERY_B, Long.toString(recoveryBRevision));
        persistent.put(KEY_WORKING, Long.toString(lastWorkingRevision));
    }

    private boolean hasJournal() {
        return persistent.get(KEY_JOURNAL_OPERATION) != null;
    }

    private static boolean isInterrupted(State value) {
        return value == State.CREATING_SNAPSHOT
                || value == State.COMMITTING
                || value == State.RESTORING
                || value == State.THAWING
                || value == State.VERIFYING
                || value == State.RECOVERY_RUNNING
                || value == State.RECOVERY_REQUIRED;
    }

    private String value(String key, String fallback) {
        String raw = persistent.get(key);
        return raw == null || raw.trim().isEmpty()
                ? fallback
                : raw;
    }

    private static Long parseLongStrict(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static State parseState(String value) {
        if (value == null) return State.NORMAL;
        try {
            return State.valueOf(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static SaveMode parseMode(String value) {
        if (value == null) return SaveMode.NORMAL;
        try {
            return SaveMode.valueOf(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void ensureState(State expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "state freeze tidak valid: " + state + " != " + expected
            );
        }
    }
}
