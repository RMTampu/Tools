package com.toolbox.tools.core;

import java.io.IOException;
import java.util.List;

public interface ProjectStore {
    ProjectLoadResult load(String projectId) throws IOException;

    ProjectState commit(ProjectState workingState, long expectedRevision) throws IOException;

    ProjectState recoverRevision(long revision) throws IOException;

    ProjectState loadRevision(long revision) throws IOException;

    List<RecoveryCandidate> recoveryCandidates() throws IOException;
}
