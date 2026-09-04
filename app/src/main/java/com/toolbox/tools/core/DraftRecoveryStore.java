package com.toolbox.tools.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DraftRecoveryStore {
    private final Path file;
    private final ProjectCodec codec = new ProjectCodec();

    public DraftRecoveryStore(File root) {
        this.file = root.toPath().resolve("recovery").resolve("draft.tbx");
    }

    public synchronized void writeDraft(ProjectState workingState) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        byte[] bytes = codec.encode(workingState).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(file.toFile(), false)) {
            stream.write(bytes);
            stream.flush();
            stream.getFD().sync();
        }
    }

    public synchronized ProjectState preview() throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return codec.decode(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            throw new IOException("draft recovery corrupt", error);
        }
    }

    public synchronized void discard() throws IOException {
        Files.deleteIfExists(file);
    }
}
