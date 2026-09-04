package com.toolbox.tools.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class InMemoryProjectStore implements ProjectStore {
    private final Map<Long, ProjectState> revisions = new TreeMap<>();
    private long currentRevision;

    @Override
    public synchronized ProjectLoadResult load(String projectId) {
        if (currentRevision == 0) {
            return new ProjectLoadResult(
                    ProjectAccessStatus.FOLDER_MISSING,
                    null,
                    Collections.emptyList()
            );
        }
        ProjectState state = revisions.get(currentRevision);
        return new ProjectLoadResult(
                ProjectAccessStatus.PROJECT_OK,
                state,
                recoveryCandidatesUnchecked()
        );
    }

    @Override
    public synchronized ProjectState commit(
            ProjectState workingState,
            long expectedRevision
    ) throws IOException {
        if (expectedRevision != currentRevision) {
            throw new StaleWriteException(expectedRevision, currentRevision);
        }
        return publish(workingState, currentRevision + 1);
    }

    @Override
    public synchronized ProjectState recoverRevision(long revision) throws IOException {
        ProjectState candidate = loadRevision(revision);
        return publish(candidate, currentRevision + 1);
    }

    private ProjectState publish(ProjectState workingState, long nextRevision)
            throws IOException {
        ProjectValidationResult validation = new ProjectValidator().validate(workingState);
        if (!validation.isPass()) {
            throw new IOException("PROJECT_VALIDATION_FAILED:" + validation.message());
        }
        ProjectState committed = workingState.withRevision(nextRevision);
        currentRevision = committed.revision();
        revisions.put(currentRevision, committed);
        while (revisions.size() > FileProjectStore.MAX_REVISIONS) {
            revisions.remove(revisions.keySet().iterator().next());
        }
        return committed;
    }

    @Override
    public synchronized ProjectState loadRevision(long revision) throws IOException {
        ProjectState state = revisions.get(revision);
        if (state == null) {
            throw new IOException("revision missing");
        }
        return state;
    }

    @Override
    public synchronized List<RecoveryCandidate> recoveryCandidates() {
        return recoveryCandidatesUnchecked();
    }

    private List<RecoveryCandidate> recoveryCandidatesUnchecked() {
        List<RecoveryCandidate> out = new ArrayList<>();
        for (Long revision : revisions.keySet()) {
            if (revision == currentRevision) {
                continue;
            }
            out.add(new RecoveryCandidate(
                    revision == currentRevision - 1
                            ? RecoveryCandidate.Kind.LAST_VALID_REVISION
                            : RecoveryCandidate.Kind.OLDER_REVISION,
                    revision,
                    0
            ));
        }
        Collections.reverse(out);
        return out;
    }
}
