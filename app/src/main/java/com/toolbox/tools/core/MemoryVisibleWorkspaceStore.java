package com.toolbox.tools.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
        if (area == null || bytes == null) throw new NullPointerException();
        data.get(area).put(
                FileVisibleWorkspaceStore.safeName(name),
                Arrays.copyOf(bytes, bytes.length)
        );
    }

    @Override
    public synchronized byte[] read(Area area, String name)
            throws IOException {
        byte[] value = data.get(area).get(
                FileVisibleWorkspaceStore.safeName(name)
        );
        if (value == null) throw new IOException("visible item missing");
        return Arrays.copyOf(value, value.length);
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
}
