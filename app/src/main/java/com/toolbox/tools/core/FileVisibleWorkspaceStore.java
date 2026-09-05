package com.toolbox.tools.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
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
        if (bytes == null || bytes.length > MAX_BYTES) {
            throw new IOException("visible item exceeds budget");
        }
        ensureLayout();
        File target = new File(directory(area), safeName(name));
        File pending = new File(target.getPath() + ".pending");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        byte[] verified = readFile(pending);
        if (!sha256(verified).equals(sha256(bytes))) {
            throw new IOException("visible item verification failed");
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("visible item replace failed");
        }
        if (!pending.renameTo(target)) {
            throw new IOException("visible item publish failed");
        }
    }

    @Override
    public synchronized byte[] read(Area area, String name) throws IOException {
        return readFile(new File(directory(area), safeName(name)));
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

    private byte[] readFile(File file) throws IOException {
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

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder out = new StringBuilder();
            for (byte value : digest.digest(bytes)) {
                out.append(String.format(Locale.ROOT, "%02x", value));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
    }
}
