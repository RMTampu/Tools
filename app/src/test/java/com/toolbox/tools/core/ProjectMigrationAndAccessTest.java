package com.toolbox.tools.core;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProjectMigrationAndAccessTest {
    @Test
    public void legacySchemaMigratesIncrementallyToCurrentSchema() {
        ProjectState legacy = ProjectState.restore(
                "project.alpha",
                0,
                ProjectState.CURRENT_BUILD_MODEL_VERSION,
                4,
                ProjectLifecycle.ACTIVE,
                Collections.singletonMap("screen.main", "one"),
                Collections.emptyMap(),
                Collections.emptySet()
        );
        ProjectMigrationRegistry registry = new ProjectMigrationRegistry();

        assertTrue(registry.canMigrate(0));
        ProjectState migrated = registry.previewMigration(legacy);

        assertEquals(ProjectState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals(4, migrated.revision());
        assertEquals("one", migrated.resources().get("screen.main"));
    }

    @Test
    public void futureSchemaFailsClosed() {
        ProjectMigrationRegistry registry = new ProjectMigrationRegistry();
        assertFalse(registry.canMigrate(ProjectState.CURRENT_SCHEMA_VERSION + 1));
    }

    @Test
    public void accessLossIsNotMisclassifiedAsCorruption() {
        ProjectAccessClassifier classifier = new ProjectAccessClassifier();

        assertEquals(
                ProjectAccessStatus.ACCESS_LOST,
                classifier.classify(false, true, true, true)
        );
        assertEquals(
                ProjectAccessStatus.FOLDER_MISSING,
                classifier.classify(true, false, true, true)
        );
        assertEquals(
                ProjectAccessStatus.RESOURCE_MISSING,
                classifier.classify(true, true, false, true)
        );
        assertEquals(
                ProjectAccessStatus.PROJECT_CORRUPT,
                classifier.classify(true, true, true, false)
        );
        assertEquals(
                ProjectAccessStatus.PROJECT_OK,
                classifier.classify(true, true, true, true)
        );
    }
}
