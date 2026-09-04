package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DataRecord {
    private final String stableKey;
    private final Map<String, String> values;

    public DataRecord(
            String stableKey,
            Map<String, String> values
    ) {
        this.stableKey = StableId.require(stableKey, "stableKey");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "fieldId"),
                        Objects.requireNonNull(entry.getValue(), "field value")
                );
            }
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public String stableKey() { return stableKey; }
    public Map<String, String> values() { return values; }
}
