package com.toolbox.tools.library;

import com.toolbox.tools.core.FileProjectStore;
import com.toolbox.tools.core.ProjectState;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LibraryDependencyLockTest {
    @Test
    public void dependencyLockRoundTripAndProjectBindingAreDeterministic()
            throws Exception {
        LibraryDependencyLock lock = LibraryDependencyLock.empty(1, 1)
                .withComponent(
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
                .withAdapter(
                        "adapter.android",
                        VersionNumber.parse("1.0.0")
                );

        String encoded = lock.encode();
        LibraryDependencyLock decoded = LibraryDependencyLock.decode(encoded);

        assertEquals(encoded, decoded.encode());
        assertEquals(
                VersionNumber.parse("1.0.0"),
                decoded.components().get("component.button")
        );

        ProjectState project = new ProjectLibraryBinder().applyLock(
                ProjectState.create("project.alpha"),
                lock
        );
        assertTrue(project.resources().containsKey(
                LibraryDependencyLock.PROJECT_RESOURCE_ID
        ));
    }

    @Test
    public void fileProjectStorePublishesLiteralDependencyLockWithRevision()
            throws Exception {
        Path root = Files.createTempDirectory("toolbox-stage3-lock");
        LibraryDependencyLock lock = LibraryDependencyLock.empty(1, 1)
                .withComponent(
                        "component.button",
                        VersionNumber.parse("1.0.0")
                );
        ProjectState project = new ProjectLibraryBinder().applyLock(
                ProjectState.create("project.alpha"),
                lock
        );

        FileProjectStore store = new FileProjectStore(root.toFile());
        ProjectState committed = store.commit(project, 0);

        Path file = root.resolve("revisions/1/dependency.lock");
        assertTrue(Files.isRegularFile(file));
        assertEquals(
                lock.encode(),
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        );
        assertEquals(committed, store.loadRevision(1));
    }

    @Test
    public void lockDoesNotResolveAgainstWrongOrMissingVersion() {
        LibraryManager library = DefaultLibraryFactory.create();
        LibraryDependencyLock good = LibraryDependencyLock.empty(1, 1)
                .withComponent(
                        "component.button",
                        VersionNumber.parse("1.0.0")
                );
        LibraryDependencyLock bad = LibraryDependencyLock.empty(1, 1)
                .withComponent(
                        "component.button",
                        VersionNumber.parse("2.0.0")
                );

        assertTrue(good.resolves(library.components(), library.assets()));
        assertFalse(bad.resolves(library.components(), library.assets()));
    }
}
