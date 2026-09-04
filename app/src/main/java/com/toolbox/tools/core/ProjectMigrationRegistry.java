package com.toolbox.tools.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ProjectMigrationRegistry {
    private final Map<Integer, ProjectMigration> migrations = new LinkedHashMap<>();

    public ProjectMigrationRegistry() {
        register(new ProjectMigration() {
            @Override
            public int fromSchemaVersion() {
                return 0;
            }

            @Override
            public int toSchemaVersion() {
                return 1;
            }

            @Override
            public ProjectState migrate(ProjectState input) {
                return input.withSchemaVersion(1);
            }
        });
    }

    public synchronized void register(ProjectMigration migration) {
        Objects.requireNonNull(migration, "migration");
        if (migration.toSchemaVersion() != migration.fromSchemaVersion() + 1) {
            throw new IllegalArgumentException("migration must be incremental");
        }
        if (migrations.put(migration.fromSchemaVersion(), migration) != null) {
            throw new IllegalArgumentException("duplicate migration source version");
        }
    }

    public synchronized boolean canMigrate(int fromVersion) {
        if (fromVersion > ProjectState.CURRENT_SCHEMA_VERSION || fromVersion < 0) {
            return false;
        }
        int current = fromVersion;
        while (current < ProjectState.CURRENT_SCHEMA_VERSION) {
            ProjectMigration migration = migrations.get(current);
            if (migration == null || migration.toSchemaVersion() != current + 1) {
                return false;
            }
            current++;
        }
        return true;
    }

    public synchronized ProjectState previewMigration(ProjectState input) {
        Objects.requireNonNull(input, "input");
        if (!canMigrate(input.schemaVersion())) {
            throw new IllegalArgumentException("migration path unavailable");
        }
        ProjectState current = input;
        while (current.schemaVersion() < ProjectState.CURRENT_SCHEMA_VERSION) {
            ProjectMigration migration = migrations.get(current.schemaVersion());
            ProjectState next = migration.migrate(current);
            if (next.schemaVersion() != migration.toSchemaVersion()) {
                throw new IllegalStateException("migration produced wrong schema");
            }
            current = next;
        }
        return current;
    }
}
