package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import java.io.IOException;
import java.util.Objects;

public final class FreezeEngine {
    public enum State {
        NORMAL,
        CREATING_SNAPSHOT,
        FROZEN,
        COMMITTING,
        RESTORING,
        THAWING,
        VERIFYING,
        RECOVERY_REQUIRED,
        FAILED_SAFE
    }

    private final ProjectManager projects;
    private State state = State.NORMAL;
    private long frozenRevision;
    private long recoveryARevision;
    private long recoveryBRevision;
    private long lastWorkingRevision;

    public FreezeEngine(ProjectManager projects) {
        this.projects = Objects.requireNonNull(projects, "projects");
    }

    public synchronized State state() { return state; }
    public synchronized long frozenRevision() { return frozenRevision; }
    public synchronized long recoveryARevision() { return recoveryARevision; }
    public synchronized long recoveryBRevision() { return recoveryBRevision; }
    public synchronized long lastWorkingRevision() { return lastWorkingRevision; }
    public synchronized boolean hasFrozenBase() {
        return state == State.FROZEN && frozenRevision > 0;
    }

    public synchronized void freeze() throws IOException {
        ensureState(State.NORMAL);
        state = State.CREATING_SNAPSHOT;
        try {
            if (projects.hasUnsavedChanges() || projects.savedRevision() <= 0) {
                projects.save();
            }
            projects.captureFinalRecoverySnapshot();
            recoveryBRevision = recoveryARevision;
            recoveryARevision = projects.savedRevision();
            frozenRevision = projects.savedRevision();
            lastWorkingRevision = frozenRevision;
            state = State.FROZEN;
        } catch (IOException | RuntimeException error) {
            state = State.FAILED_SAFE;
            throw error;
        }
    }

    public synchronized ProjectState recover() throws IOException {
        ensureState(State.FROZEN);
        state = State.RESTORING;
        try {
            lastWorkingRevision = projects.savedRevision();
            ProjectState restored = projects.restoreRevision(frozenRevision);
            projects.captureFinalRecoverySnapshot();
            state = State.FROZEN;
            return restored;
        } catch (IOException | RuntimeException error) {
            state = State.RECOVERY_REQUIRED;
            throw error;
        }
    }

    public synchronized ProjectState commit() throws IOException {
        ensureState(State.FROZEN);
        state = State.COMMITTING;
        try {
            ProjectState committed = projects.hasUnsavedChanges()
                    ? projects.save()
                    : projects.current();
            projects.captureFinalRecoverySnapshot();
            recoveryBRevision = recoveryARevision;
            recoveryARevision = frozenRevision;
            lastWorkingRevision = committed.revision();
            frozenRevision = committed.revision();
            state = State.FROZEN;
            return committed;
        } catch (IOException | RuntimeException error) {
            state = State.RECOVERY_REQUIRED;
            throw error;
        }
    }

    public synchronized void thaw() {
        ensureState(State.FROZEN);
        state = State.THAWING;
        frozenRevision = 0;
        lastWorkingRevision = projects.savedRevision();
        state = State.NORMAL;
    }

    private void ensureState(State expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "state freeze tidak valid: " + state + " != " + expected
            );
        }
    }
}
