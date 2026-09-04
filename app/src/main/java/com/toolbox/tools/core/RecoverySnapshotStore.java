package com.toolbox.tools.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class RecoverySnapshotStore {
    private final Path directory;
    private final ProjectCodec codec = new ProjectCodec();
    private ProjectState memoryFinal;
    private ProjectState memoryLastValid;

    public RecoverySnapshotStore() {
        this.directory = null;
    }

    public RecoverySnapshotStore(File projectRoot) {
        this.directory = projectRoot.toPath().resolve("recovery");
    }

    public synchronized void captureLastValid(ProjectState state) throws IOException {
        write(RecoveryCandidate.Kind.LAST_VALID_RECOVERY, state);
    }

    public synchronized void captureFinal(ProjectState state) throws IOException {
        write(RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT, state);
    }

    public synchronized ProjectState preview(RecoveryCandidate.Kind kind)
            throws IOException {
        switch (kind) {
            case FINAL_RECOVERY_SNAPSHOT:
                return read(kind);
            case LAST_VALID_RECOVERY:
                return read(kind);
            default:
                throw new IllegalArgumentException(
                        "snapshot kind not stored here: " + kind
                );
        }
    }

    public synchronized List<RecoveryCandidate> candidates() throws IOException {
        List<RecoveryCandidate> out = new ArrayList<>();
        addCandidate(out, RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT);
        addCandidate(out, RecoveryCandidate.Kind.LAST_VALID_RECOVERY);
        return out;
    }

    private void addCandidate(
            List<RecoveryCandidate> out,
            RecoveryCandidate.Kind kind
    ) throws IOException {
        ProjectState state = read(kind);
        if (state == null) {
            return;
        }
        long size;
        if (directory == null) {
            size = codec.encode(state).getBytes(StandardCharsets.UTF_8).length;
        } else {
            size = Files.size(path(kind));
        }
        out.add(new RecoveryCandidate(kind, state.revision(), size));
    }

    private void write(
            RecoveryCandidate.Kind kind,
            ProjectState state
    ) throws IOException {
        if (state == null) {
            throw new NullPointerException("state");
        }
        String encoded = codec.encode(state);
        ProjectState verified;
        try {
            verified = codec.decode(encoded);
        } catch (IllegalArgumentException error) {
            throw new IOException("recovery snapshot verification failed", error);
        }
        if (!verified.equals(state)) {
            throw new IOException("recovery snapshot round-trip mismatch");
        }

        if (directory == null) {
            if (kind == RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT) {
                memoryFinal = state;
            } else if (kind == RecoveryCandidate.Kind.LAST_VALID_RECOVERY) {
                memoryLastValid = state;
            } else {
                throw new IllegalArgumentException("unsupported snapshot kind");
            }
            return;
        }

        Files.createDirectories(directory);
        Path target = path(kind);
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
        try {
            try (FileOutputStream stream = new FileOutputStream(temp.toFile(), false)) {
                stream.write(bytes);
                stream.flush();
                stream.getFD().sync();
            }
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                throw new IOException("atomic recovery snapshot publish unavailable", error);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private ProjectState read(RecoveryCandidate.Kind kind) throws IOException {
        if (directory == null) {
            if (kind == RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT) {
                return memoryFinal;
            }
            if (kind == RecoveryCandidate.Kind.LAST_VALID_RECOVERY) {
                return memoryLastValid;
            }
            throw new IllegalArgumentException("unsupported snapshot kind");
        }

        Path target = path(kind);
        if (!Files.isRegularFile(target)) {
            return null;
        }
        try {
            return codec.decode(
                    new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
            );
        } catch (IllegalArgumentException error) {
            throw new IOException("recovery snapshot corrupt", error);
        }
    }

    private Path path(RecoveryCandidate.Kind kind) {
        if (kind == RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT) {
            return directory.resolve("final-recovery.tbx");
        }
        if (kind == RecoveryCandidate.Kind.LAST_VALID_RECOVERY) {
            return directory.resolve("last-valid-recovery.tbx");
        }
        throw new IllegalArgumentException("unsupported snapshot kind");
    }
}
