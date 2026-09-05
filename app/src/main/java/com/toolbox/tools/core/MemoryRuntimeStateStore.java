package com.toolbox.tools.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MemoryRuntimeStateStore implements RuntimeStateStore {
    private final Map<String, String> values = new LinkedHashMap<>();

    @Override
    public synchronized String get(String key) {
        return values.get(StableId.require(key, "runtimeStateKey"));
    }

    @Override
    public synchronized void put(String key, String value) {
        values.put(
                StableId.require(key, "runtimeStateKey"),
                java.util.Objects.requireNonNull(value, "value")
        );
    }

    @Override
    public synchronized void remove(String key) {
        values.remove(StableId.require(key, "runtimeStateKey"));
    }

    @Override
    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
