package com.toolbox.tools.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileStorageGateway implements StorageGateway {
    private static final long MAX_FILE_BYTES = 1_048_576;

    private final Path target;
    private final Path backup;
    private final WorkspaceCodec codec;
    private boolean recoveredFromBackup;

    public FileStorageGateway(File target) {
        this(target.toPath(), new WorkspaceCodec());
    }

    FileStorageGateway(Path target, WorkspaceCodec codec) {
        this.target = target;
        this.backup = target.resolveSibling(target.getFileName().toString() + ".bak");
        this.codec = codec;
    }

    @Override
    public synchronized boolean exists() {
        return Files.isRegularFile(target) || Files.isRegularFile(backup);
    }

    @Override
    public synchronized WorkspaceSnapshot load() throws IOException {
        recoveredFromBackup = false;
        IOException primaryFailure;
        try {
            return read(target);
        } catch (IOException error) {
            primaryFailure = error;
        }

        if (!Files.isRegularFile(backup)) {
            throw primaryFailure;
        }

        try {
            WorkspaceSnapshot recovered = read(backup);
            recoveredFromBackup = true;
            return recovered;
        } catch (IOException backupFailure) {
            primaryFailure.addSuppressed(backupFailure);
            throw primaryFailure;
        }
    }

    @Override
    public synchronized void save(WorkspaceSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        byte[] payload = codec.encode(snapshot).getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_FILE_BYTES) {
            throw new IOException("workspace payload exceeds storage budget");
        }

        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("workspace target has no parent");
        }
        Files.createDirectories(parent);

        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Path backupTemp = backup.resolveSibling(backup.getFileName().toString() + ".tmp");

        try {
            if (Files.isRegularFile(target)) {
                Files.copy(target, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                sync(backupTemp);
                atomicReplace(backupTemp, backup);
            }

            writeAndSync(temp, payload);
            atomicReplace(temp, target);
            recoveredFromBackup = false;
        } finally {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(backupTemp);
        }
    }

    @Override
    public synchronized boolean recoveredFromBackup() {
        return recoveredFromBackup;
    }

    public synchronized boolean wasRecoveredFromBackup() {
        return recoveredFromBackup;
    }

    private WorkspaceSnapshot read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("workspace file missing: " + path.getFileName());
        }
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) {
            throw new IOException("workspace file size invalid");
        }
        try {
            return codec.decode(new String(
                    Files.readAllBytes(path),
                    StandardCharsets.UTF_8
            ));
        } catch (IllegalArgumentException error) {
            throw new IOException("workspace file corrupt", error);
        }
    }

    private static void writeAndSync(Path path, byte[] payload) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(path.toFile(), false)) {
            stream.write(payload);
            stream.flush();
            stream.getFD().sync();
        }
    }

    private static void sync(Path path) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(path.toFile(), true)) {
            stream.getFD().sync();
        }
    }

    private static void atomicReplace(Path from, Path to) throws IOException {
        try {
            Files.move(
                    from,
                    to,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException error) {
            throw new IOException("atomic workspace replacement unavailable", error);
        }
    }
}
