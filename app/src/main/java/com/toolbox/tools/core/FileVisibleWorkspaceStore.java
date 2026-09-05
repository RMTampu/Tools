package com.toolbox.tools.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FileVisibleWorkspaceStore implements VisibleWorkspaceStore {
    private static final int MAX_BYTES = 128 * 1024 * 1024;
    private final File root;

    public FileVisibleWorkspaceStore(File root) {
        this.root = java.util.Objects.requireNonNull(root, "root");
    }

    @Override
    public synchronized void ensureLayout() throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("workspace root unavailable");
        }
        for (Area area : Area.values()) {
            File dir = directory(area);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("workspace area unavailable:" + area);
            }
        }
    }

    @Override
    public synchronized void write(
            Area area,
            String name,
            byte[] bytes
    ) throws IOException {
        if (bytes == null) throw new NullPointerException("bytes");
        writeStream(
                area,
                name,
                new ByteArrayInputStream(bytes),
                MAX_BYTES
        );
    }

    @Override
    public synchronized WriteResult writeStream(
            Area area,
            String name,
            InputStream input,
            long maxBytes
    ) throws IOException {
        if (input == null) throw new NullPointerException("input");
        if (maxBytes <= 0 || maxBytes > MAX_BYTES) {
            throw new IOException("visible stream budget invalid");
        }
        ensureLayout();
        File target = new File(directory(area), safeName(name));
        File pending = new File(target.getPath() + ".pending");
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (FileOutputStream output = new FileOutputStream(pending)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("visible item exceeds budget");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            pending.delete();
            throw error;
        }

        String expected = hex(digest.digest());
        if (!expected.equals(digestFile(pending, maxBytes))) {
            pending.delete();
            throw new IOException("visible item verification failed");
        }
        if (target.exists() && !target.delete()) {
            pending.delete();
            throw new IOException("visible item replace failed");
        }
        if (!pending.renameTo(target)) {
            pending.delete();
            throw new IOException("visible item publish failed");
        }
        return new WriteResult(total, expected);
    }

    @Override
    public synchronized byte[] read(Area area, String name) throws IOException {
        File file = new File(directory(area), safeName(name));
        if (!file.isFile()) throw new IOException("visible item missing");
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("visible item exceeds budget");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    @Override
    public synchronized boolean exists(Area area, String name) {
        return new File(directory(area), safeName(name)).isFile();
    }

    @Override
    public synchronized List<String> list(Area area) throws IOException {
        ensureLayout();
        String[] names = directory(area).list();
        if (names == null) throw new IOException("visible area unreadable");
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (!name.endsWith(".pending")) out.add(name);
        }
        Collections.sort(out);
        return Collections.unmodifiableList(out);
    }

    private File directory(Area area) {
        return new File(
                root,
                java.util.Objects.requireNonNull(area, "area").folder()
        );
    }

    public static String safeName(String name) {
        if (name == null
                || !name.matches("[A-Za-z0-9._-]{1,128}")
                || name.contains("..")) {
            throw new IllegalArgumentException(
                    "visible workspace name invalid"
            );
        }
        return name;
    }

    private static String digestFile(File file, long maxBytes)
            throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("visible item exceeds budget");
                }
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value));
        }
        return out.toString();
    }
}
