package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BackStackEntry {
    private final String screenId;
    private final Map<String, String> parameters;

    public BackStackEntry(String screenId, Map<String, String> parameters) {
        this.screenId = StableId.require(screenId, "screenId");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "parameterId"),
                        Objects.requireNonNull(entry.getValue(), "parameterValue")
                );
            }
        }
        if (copy.size() > 32) {
            throw new IllegalArgumentException("too many navigation parameters");
        }
        this.parameters = Collections.unmodifiableMap(copy);
    }

    public String screenId() { return screenId; }
    public Map<String, String> parameters() { return parameters; }
}
