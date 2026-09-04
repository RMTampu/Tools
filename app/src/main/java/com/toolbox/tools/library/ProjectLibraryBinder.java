package com.toolbox.tools.library;

import com.toolbox.tools.core.ProjectState;
import java.util.Objects;

public final class ProjectLibraryBinder {
    public ProjectState applyLock(
            ProjectState project,
            LibraryDependencyLock lock
    ) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(lock, "lock");
        if (lock.projectSchemaVersion() != project.schemaVersion()) {
            throw new IllegalArgumentException("dependency.lock schema mismatch");
        }
        if (lock.buildModelVersion() != project.buildModelVersion()) {
            throw new IllegalArgumentException("dependency.lock build model mismatch");
        }
        return project.withResource(
                LibraryDependencyLock.PROJECT_RESOURCE_ID,
                lock.encode()
        );
    }

    public LibraryDependencyLock readLock(ProjectState project) {
        Objects.requireNonNull(project, "project");
        String encoded = project.resources().get(
                LibraryDependencyLock.PROJECT_RESOURCE_ID
        );
        if (encoded == null) return null;
        LibraryDependencyLock lock = LibraryDependencyLock.decode(encoded);
        if (lock.projectSchemaVersion() != project.schemaVersion()
                || lock.buildModelVersion() != project.buildModelVersion()) {
            throw new IllegalArgumentException("dependency.lock project mismatch");
        }
        return lock;
    }
}
