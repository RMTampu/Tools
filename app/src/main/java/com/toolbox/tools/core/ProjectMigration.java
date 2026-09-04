package com.toolbox.tools.core;

public interface ProjectMigration {
    int fromSchemaVersion();

    int toSchemaVersion();

    ProjectState migrate(ProjectState input);
}
