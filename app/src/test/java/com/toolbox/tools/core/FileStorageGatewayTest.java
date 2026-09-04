package com.toolbox.tools.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FileStorageGatewayTest {
    @Test
    public void saveAndLoadRoundTripUsesAtomicFile() throws Exception {
        Path directory = Files.createTempDirectory("toolbox-stage2");
        FileStorageGateway storage =
                new FileStorageGateway(directory.resolve("workspace.tbx").toFile());

        WorkspaceSnapshot snapshot = WorkspaceSnapshot.create("toolbox.test")
                .withValue("title", "Alpha", 1);

        storage.save(snapshot);

        assertTrue(storage.exists());
        assertEquals(snapshot, storage.load());
        assertFalse(storage.wasRecoveredFromBackup());
    }

    @Test
    public void corruptPrimaryFallsBackToLastValidBackup() throws Exception {
        Path directory = Files.createTempDirectory("toolbox-stage2");
        Path target = directory.resolve("workspace.tbx");
        FileStorageGateway storage = new FileStorageGateway(target.toFile());

        WorkspaceSnapshot first = WorkspaceSnapshot.create("toolbox.test")
                .withValue("title", "Alpha", 1);
        WorkspaceSnapshot second = first.withValue("title", "Beta", 2);

        storage.save(first);
        storage.save(second);
        Files.writeString(target, "corrupt\n", StandardCharsets.UTF_8);

        WorkspaceSnapshot recovered = storage.load();

        assertEquals(first, recovered);
        assertTrue(storage.wasRecoveredFromBackup());
    }
}
