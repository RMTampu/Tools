package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ValueConverterRegistry {
    public interface Converter {
        Object convert(Object value);
    }

    public static final class Entry {
        private final String converterId;
        private final ValueType from;
        private final ValueType to;
        private final Converter converter;

        Entry(
                String converterId,
                ValueType from,
                ValueType to,
                Converter converter
        ) {
            this.converterId = converterId;
            this.from = from;
            this.to = to;
            this.converter = converter;
        }

        public String converterId() { return converterId; }
        public ValueType from() { return from; }
        public ValueType to() { return to; }

        public Object convert(Object value) {
            return converter.convert(value);
        }
    }

    private final Map<String, Entry> byId =
            new LinkedHashMap<>();
    private final Map<String, String> byPair =
            new LinkedHashMap<>();

    public synchronized void register(
            String converterId,
            ValueType from,
            ValueType to,
            Converter converter
    ) {
        String id = StableId.require(
                converterId,
                "converterId"
        );
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(converter, "converter");
        if (from == to) {
            throw new IllegalArgumentException(
                    "converter for identical type is unnecessary"
            );
        }
        String pair = pair(from, to);
        if (byId.containsKey(id)
                || byPair.containsKey(pair)) {
            throw new IllegalArgumentException(
                    "converter duplicate"
            );
        }
        Entry entry = new Entry(
                id,
                from,
                to,
                converter
        );
        byId.put(id, entry);
        byPair.put(pair, id);
    }

    public synchronized Entry resolve(
            ValueType from,
            ValueType to
    ) {
        if (from == to) return null;
        String id = byPair.get(pair(from, to));
        return id == null ? null : byId.get(id);
    }

    public synchronized Entry resolve(String converterId) {
        return byId.get(
                StableId.require(
                        converterId,
                        "converterId"
                )
        );
    }

    public synchronized boolean canConvert(
            ValueType from,
            ValueType to
    ) {
        return from == to || resolve(from, to) != null;
    }

    public synchronized Object convert(
            ValueType from,
            ValueType to,
            Object value
    ) {
        if (from == to) return value;
        Entry entry = resolve(from, to);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "explicit converter unavailable"
            );
        }
        return entry.convert(value);
    }

    public synchronized Map<String, Entry> all() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(byId)
        );
    }

    public static ValueConverterRegistry defaults() {
        ValueConverterRegistry registry =
                new ValueConverterRegistry();
        registry.register(
                "converter.number.text",
                ValueType.NUMBER,
                ValueType.TEXT,
                value -> {
                    if (!(value instanceof Number)) {
                        throw new IllegalArgumentException(
                                "number converter input invalid"
                        );
                    }
                    return String.valueOf(value);
                }
        );
        registry.register(
                "converter.boolean.text",
                ValueType.BOOLEAN,
                ValueType.TEXT,
                value -> {
                    if (!(value instanceof Boolean)) {
                        throw new IllegalArgumentException(
                                "boolean converter input invalid"
                        );
                    }
                    return String.valueOf(value);
                }
        );
        registry.register(
                "converter.reference.text",
                ValueType.REFERENCE,
                ValueType.TEXT,
                value -> {
                    if (!(value instanceof String)
                            || ((String) value).trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "reference converter input invalid"
                        );
                    }
                    return value;
                }
        );
        registry.register(
                "converter.text.number",
                ValueType.TEXT,
                ValueType.NUMBER,
                value -> {
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException(
                                "text converter input invalid"
                        );
                    }
                    String raw = ((String) value).trim();
                    if (!raw.matches("-?[0-9]+(\\.[0-9]+)?")) {
                        throw new IllegalArgumentException(
                                "text number conversion unsafe"
                        );
                    }
                    double parsed = Double.parseDouble(raw);
                    if (!Double.isFinite(parsed)) {
                        throw new IllegalArgumentException(
                                "text number conversion invalid"
                        );
                    }
                    return parsed;
                }
        );
        return registry;
    }

    private static String pair(
            ValueType from,
            ValueType to
    ) {
        return from.name() + "->" + to.name();
    }
}
