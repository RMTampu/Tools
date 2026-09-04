package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class LocalizationManager {
    public static final String BAHASA_DEFAULT = "id";
    private final Map<String, Map<String, String>> values = new LinkedHashMap<>();

    public synchronized void put(
            String stringId,
            String localeTag,
            String value
    ) {
        String id = StableId.require(stringId, "stringId");
        String locale = requireLocale(localeTag);
        String text = Objects.requireNonNull(value, "value").trim();
        if (text.isEmpty()) throw new IllegalArgumentException("teks kosong");
        values.computeIfAbsent(id, ignored -> new LinkedHashMap<>())
                .put(locale, text);
    }

    public synchronized String resolve(String stringId, String localeTag) {
        String id = StableId.require(stringId, "stringId");
        Map<String, String> variants = values.get(id);
        if (variants == null) return id;
        String locale = requireLocale(localeTag);
        String direct = variants.get(locale);
        if (direct != null) return direct;
        String indonesia = variants.get(BAHASA_DEFAULT);
        if (indonesia != null) return indonesia;
        return variants.values().iterator().next();
    }

    public synchronized Map<String, String> indonesia() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : values.entrySet()) {
            String value = entry.getValue().get(BAHASA_DEFAULT);
            if (value != null) out.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(out);
    }

    private static String requireLocale(String value) {
        Objects.requireNonNull(value, "localeTag");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2,3}(-[a-z0-9]{2,8})*")) {
            throw new IllegalArgumentException("locale tidak valid");
        }
        return normalized;
    }
}
