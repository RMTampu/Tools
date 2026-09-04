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

    public FreezeEngine(ProjectManager projects) {
        this.projects = Objects.requireNonNull(projects, "projects");
    }

    public synchronized State state() { return state; }
    public synchronized long frozenRevision() { return frozenRevision; }

    public synchronized void freeze() throws IOException {
        ensureState(State.NORMAL);
        state = State.CREATING_SNAPSHOT;
        try {
            if (projects.hasUnsavedChanges() || projects.savedRevision() <= 0) {
                projects.save();
            }
            projects.captureFinalRecoverySnapshot();
            frozenRevision = projects.savedRevision();
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
            ProjectState restored = projects.restoreRevision(frozenRevision);
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
