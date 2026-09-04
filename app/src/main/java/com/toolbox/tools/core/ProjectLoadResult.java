package com.toolbox.tools.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProjectLoadResult {
    private final ProjectAccessStatus status;
    private final ProjectState state;
    private final List<RecoveryCandidate> recoveryCandidates;

    public ProjectLoadResult(
            ProjectAccessStatus status,
            ProjectState state,
            List<RecoveryCandidate> recoveryCandidates
    ) {
        this.status = status;
        this.state = state;
        this.recoveryCandidates = Collections.unmodifiableList(
                new ArrayList<>(recoveryCandidates)
        );
    }

    public ProjectAccessStatus status() {
        return status;
    }

    public ProjectState state() {
        return state;
    }

    public List<RecoveryCandidate> recoveryCandidates() {
        return recoveryCandidates;
    }
}
