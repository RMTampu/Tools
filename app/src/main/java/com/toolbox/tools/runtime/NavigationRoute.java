package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class NavigationRoute {
    private final String routeId;
    private final String targetScreenId;
    private final Map<String, ValueType> parameters;

    public NavigationRoute(
            String routeId,
            String targetScreenId,
            Map<String, ValueType> parameters
    ) {
        this.routeId = StableId.require(routeId, "routeId");
        this.targetScreenId = StableId.require(targetScreenId, "targetScreenId");
        LinkedHashMap<String, ValueType> copy = new LinkedHashMap<>();
        if (parameters != null) {
            for (Map.Entry<String, ValueType> entry : parameters.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "parameterId"),
                        Objects.requireNonNull(entry.getValue(), "parameterType")
                );
            }
        }
        this.parameters = Collections.unmodifiableMap(copy);
    }

    public String routeId() { return routeId; }
    public String targetScreenId() { return targetScreenId; }
    public Map<String, ValueType> parameters() { return parameters; }
}
