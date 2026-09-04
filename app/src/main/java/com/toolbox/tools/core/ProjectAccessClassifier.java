package com.toolbox.tools.core;

public final class ProjectAccessClassifier {
    public ProjectAccessStatus classify(
            boolean permissionAvailable,
            boolean folderExists,
            boolean requiredResourcesPresent,
            boolean integrityValid
    ) {
        if (!permissionAvailable) {
            return ProjectAccessStatus.ACCESS_LOST;
        }
        if (!folderExists) {
            return ProjectAccessStatus.FOLDER_MISSING;
        }
        if (!requiredResourcesPresent) {
            return ProjectAccessStatus.RESOURCE_MISSING;
        }
        if (!integrityValid) {
            return ProjectAccessStatus.PROJECT_CORRUPT;
        }
        return ProjectAccessStatus.PROJECT_OK;
    }
}
