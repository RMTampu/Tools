package com.toolbox.tools.core;

public final class ProjectRelinkVerifier {
    public ProjectAccessStatus verify(
            String expectedProjectId,
            ProjectState candidate,
            boolean manifestIntegrityValid
    ) {
        StableId.require(expectedProjectId, "expectedProjectId");
        if (candidate == null) {
            return ProjectAccessStatus.RESOURCE_MISSING;
        }
        if (!manifestIntegrityValid) {
            return ProjectAccessStatus.PROJECT_CORRUPT;
        }
        if (!expectedProjectId.equals(candidate.projectId())) {
            return ProjectAccessStatus.PROJECT_CORRUPT;
        }
        if (candidate.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION) {
            return ProjectAccessStatus.SCHEMA_INCOMPATIBLE;
        }
        return ProjectAccessStatus.PROJECT_OK;
    }
}
