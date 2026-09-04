package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class FileAssetStore implements AssetStore {
    private final Path root;
    private final Path originals;
    private final Path cache;

    public FileAssetStore(File root) {
        this.root = root.toPath();
        this.originals = this.root.resolve("originals");
        this.cache = this.root.resolve("cache");
    }

    public synchronized void importOriginal(
            AssetDescriptor descriptor,
            byte[] bytes
    ) throws IOException {
        verifyPayload(descriptor, bytes);
        Path target = originalPath(descriptor);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            byte[] existing = Files.readAllBytes(target);
            if (!DigestUtils.sha256(existing).equals(descriptor.sha256())) {
                throw new IOException("existing original integrity mismatch");
            }
            return;
        }

        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            writeAndSync(temp, bytes);
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                throw new IOException("atomic asset publish unavailable", error);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public synchronized AssetStatus status(AssetDescriptor descriptor)
            throws IOException {
        Path target = originalPath(descriptor);
        if (!Files.isRegularFile(target)) return AssetStatus.MISSING_ASSET;
        byte[] bytes = Files.readAllBytes(target);
        if (bytes.length > descriptor.maxBytes()) {
            return AssetStatus.INCOMPATIBLE_ASSET;
        }
        return DigestUtils.sha256(bytes).equals(descriptor.sha256())
                ? AssetStatus.AVAILABLE
                : AssetStatus.BROKEN_ASSET_INTEGRITY;
    }

    public synchronized byte[] readOriginal(AssetDescriptor descriptor)
            throws IOException {
        AssetStatus status = status(descriptor);
        if (status != AssetStatus.AVAILABLE) {
            throw new IOException("asset original unavailable: " + status);
        }
        return Files.readAllBytes(originalPath(descriptor));
    }

    public synchronized void relinkOriginal(
            AssetDescriptor descriptor,
            byte[] candidate
    ) throws IOException {
        verifyPayload(descriptor, candidate);
        Path target = originalPath(descriptor);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName().toString() + ".relink.tmp");
        try {
            writeAndSync(temp, candidate);
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                throw new IOException("atomic asset relink unavailable", error);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public synchronized void writePreviewCache(
            AssetDescriptor descriptor,
            byte[] preview
    ) throws IOException {
        if (preview == null || preview.length > Math.min(descriptor.maxBytes(), 512 * 1024L)) {
            throw new IOException("preview cache exceeds budget");
        }
        Path target = cachePath(descriptor);
        Files.createDirectories(target.getParent());
        Files.write(target, preview);
    }

    public synchronized void clearCache() throws IOException {
        deleteTree(cache);
        Files.createDirectories(cache);
    }

    public synchronized boolean originalExists(AssetDescriptor descriptor) {
        return Files.isRegularFile(originalPath(descriptor));
    }

    public synchronized boolean previewExists(AssetDescriptor descriptor) {
        return Files.isRegularFile(cachePath(descriptor));
    }

    private void verifyPayload(AssetDescriptor descriptor, byte[] bytes)
            throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("asset payload empty");
        }
        if (bytes.length > descriptor.maxBytes()) {
            throw new IOException("asset payload exceeds contract budget");
        }
        if (!DigestUtils.sha256(bytes).equals(descriptor.sha256())) {
            throw new IOException("asset payload SHA-256 mismatch");
        }
    }

    private Path originalPath(AssetDescriptor descriptor) {
        Path path = originals
                .resolve(encoded(descriptor.assetId()))
                .resolve(descriptor.version().toString())
                .resolve("original.bin");
        ensureChild(originals, path);
        return path;
    }

    private Path cachePath(AssetDescriptor descriptor) {
        Path path = cache
                .resolve(encoded(descriptor.assetId()))
                .resolve(descriptor.version().toString())
                .resolve("preview.bin");
        ensureChild(cache, path);
        return path;
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void ensureChild(Path parent, Path child) {
        Path p = parent.toAbsolutePath().normalize();
        Path c = child.toAbsolutePath().normalize();
        if (!c.startsWith(p)) {
            throw new IllegalArgumentException("asset path escaped library boundary");
        }
    }

    private static void writeAndSync(Path path, byte[] bytes) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(path.toFile(), false)) {
            stream.write(bytes);
            stream.flush();
            stream.getFD().sync();
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
            List<Path> items = stream.sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path item : items) Files.deleteIfExists(item);
        }
    }
}
