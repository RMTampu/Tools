package com.toolbox.tools.repair;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.RecoveryCandidate;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class RecoveryPreviewService {
    private final ProjectManager projectManager;

    public RecoveryPreviewService(ProjectManager projectManager) {
        this.projectManager = Objects.requireNonNull(
                projectManager,
                "projectManager"
        );
    }

    public List<RecoveryCandidate> candidates() throws IOException {
        return projectManager.recoveryCandidates();
    }

    public ProjectState preview(
            RecoveryCandidate candidate
    ) throws IOException {
        return projectManager.previewRecoveryCandidate(candidate);
    }

    public ProjectState restoreExplicit(
            RecoveryCandidate candidate
    ) throws IOException {
        return projectManager.restoreRecoveryCandidate(candidate);
    }
}
