package com.toolbox.tools.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ConfigStore {
    private final Map<String, String> values = new LinkedHashMap<>();

    public synchronized void put(String key, String value) {
        values.put(requireText(key, "key"), requireText(value, "value"));
    }

    public synchronized String get(String key, String fallback) {
        String value = values.get(requireText(key, "key"));
        return value == null ? fallback : value;
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
