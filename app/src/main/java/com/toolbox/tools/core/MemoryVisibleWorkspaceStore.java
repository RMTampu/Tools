package com.toolbox.tools.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MemoryVisibleWorkspaceStore implements VisibleWorkspaceStore {
    private final Map<Area, Map<String, byte[]>> data =
            new LinkedHashMap<>();

    public MemoryVisibleWorkspaceStore() {
        for (Area area : Area.values()) {
            data.put(area, new LinkedHashMap<>());
        }
    }

    @Override
    public void ensureLayout() {
        // Struktur area sudah dibuat pada konstruktor.
    }

    @Override
    public synchronized void write(
            Area area,
            String name,
            byte[] bytes
    ) {
        if (area == null || bytes == null) {
            throw new NullPointerException();
        }
        data.get(area).put(
                FileVisibleWorkspaceStore.safeName(name),
                Arrays.copyOf(bytes, bytes.length)
        );
    }

    @Override
    public synchronized WriteResult writeStream(
            Area area,
            String name,
            InputStream input,
            long maxBytes
    ) throws IOException {
        if (input == null) {
            throw new NullPointerException("input");
        }
        if (maxBytes <= 0) {
            throw new IOException("visible stream budget invalid");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }

        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("visible item exceeds budget");
            }
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }

        write(area, name, output.toByteArray());
        return new WriteResult(total, hex(digest.digest()));
    }

    @Override
    public synchronized byte[] read(Area area, String name)
            throws IOException {
        byte[] value = data.get(area).get(
                FileVisibleWorkspaceStore.safeName(name)
        );
        if (value == null) {
            throw new IOException("visible item missing");
        }
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public synchronized InputStream openInputStream(
            Area area,
            String name
    ) throws IOException {
        return new ByteArrayInputStream(read(area, name));
    }

    @Override
    public synchronized boolean exists(Area area, String name) {
        return data.get(area).containsKey(
                FileVisibleWorkspaceStore.safeName(name)
        );
    }

    @Override
    public synchronized List<String> list(Area area) {
        List<String> out = new ArrayList<>(data.get(area).keySet());
        Collections.sort(out);
        return Collections.unmodifiableList(out);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value));
        }
        return out.toString();
    }
}
