package com.toolbox.tools.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class FileProjectStoreTest {
    @Test
    public void saveCreatesHybridDefinitionManifestIndexAndResourcePackage()
            throws Exception {
        Path root = Files.createTempDirectory("toolbox-stage2-project");
        FileProjectStore store = new FileProjectStore(root.toFile());

        ProjectState working = ProjectState.create("project.alpha")
                .withResource("screen.main", "screen-data")
                .withResource("asset.logo", "asset-data")
                .withReference("screen.main", "asset.logo");

        ProjectState committed = store.commit(working, 0);
        Path revision = root.resolve("revisions/1");

        assertEquals(1, committed.revision());
        assertTrue(Files.isRegularFile(revision.resolve("project.json")));
        assertTrue(Files.isRegularFile(revision.resolve("project.manifest")));
        assertTrue(Files.isRegularFile(revision.resolve("project.index")));
        assertTrue(Files.isRegularFile(
                revision.resolve("resources")
                        .resolve(ProjectDefinitionCodec.resourceFileName("screen.main"))
        ));
        String definition = new String(
                Files.readAllBytes(revision.resolve("project.json")),
                StandardCharsets.UTF_8
        );
        assertFalse(definition.contains("screen-data"));
        assertEquals(committed, store.loadRevision(1));
    }

    @Test
    public void corruptCurrentRevisionFallsBackThenExplicitRestorePublishesNewRevision()
            throws Exception {
        Path root = Files.createTempDirectory("toolbox-stage2-recovery");
        FileProjectStore store = new FileProjectStore(root.toFile());

        ProjectState first = store.commit(
                ProjectState.create("project.alpha")
                        .withResource("screen.main", "one"),
                0
        );
        ProjectState second = store.commit(
                first.withResource("screen.main", "two"),
                1
        );

        Path currentResource = root.resolve("revisions/2/resources")
                .resolve(ProjectDefinitionCodec.resourceFileName("screen.main"));
        Files.write(
                currentResource,
                "corrupt".getBytes(StandardCharsets.UTF_8)
        );

        ProjectLoadResult load = store.load("project.alpha");
        assertEquals(ProjectAccessStatus.PROJECT_CORRUPT, load.status());
        assertNotNull(load.state());
        assertEquals(1, load.state().revision());
        assertEquals("one", load.state().resources().get("screen.main"));

        ProjectState recovered = store.recoverRevision(1);
        assertEquals(3, recovered.revision());
        assertEquals("one", recovered.resources().get("screen.main"));

        ProjectLoadResult after = store.load("project.alpha");
        assertEquals(ProjectAccessStatus.PROJECT_OK, after.status());
        assertEquals(3, after.state().revision());
    }

    @Test
    public void interruptedUnpublishedRevisionIsRemovedOnNextLoad() throws Exception {
        Path root = Files.createTempDirectory("toolbox-stage2-journal");
        FileProjectStore store = new FileProjectStore(root.toFile());

        store.commit(
                ProjectState.create("project.alpha")
                        .withResource("screen.main", "one"),
                0
        );

        Path unfinished = root.resolve("revisions/2");
        Files.createDirectories(unfinished);
        Files.write(
                unfinished.resolve("garbage"),
                "x".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
                root.resolve("journal.pending"),
                "next=2\npreviousValid=1\n".getBytes(StandardCharsets.UTF_8)
        );

        ProjectLoadResult load = store.load("project.alpha");

        assertEquals(ProjectAccessStatus.PROJECT_OK, load.status());
        assertFalse(Files.exists(unfinished));
        assertFalse(Files.exists(root.resolve("journal.pending")));
        assertEquals(1, load.state().revision());
    }
}
