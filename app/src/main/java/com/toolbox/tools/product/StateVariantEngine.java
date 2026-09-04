package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StateVariantEngine {
    private final Map<String, Map<String, String>> normal = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, String>>> deltas = new LinkedHashMap<>();

    public synchronized void setNormal(String objectId, String propertyId, String value) {
        String object = StableId.require(objectId, "objectId");
        String property = StableId.require(propertyId, "propertyId");
        normal.computeIfAbsent(object, ignored -> new LinkedHashMap<>())
                .put(property, Objects.requireNonNull(value, "value"));
    }

    public synchronized void setStateOverride(
            String objectId,
            String stateId,
            String propertyId,
            String value
    ) {
        String object = StableId.require(objectId, "objectId");
        String state = StableId.require(stateId, "stateId");
        String property = StableId.require(propertyId, "propertyId");
        deltas.computeIfAbsent(object, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(state, ignored -> new LinkedHashMap<>())
                .put(property, Objects.requireNonNull(value, "value"));
    }

    public synchronized void resetState(String objectId, String stateId) {
        Map<String, Map<String, String>> byState =
                deltas.get(StableId.require(objectId, "objectId"));
        if (byState != null) byState.remove(StableId.require(stateId, "stateId"));
    }

    public synchronized Map<String, String> resolve(String objectId, String stateId) {
        String object = StableId.require(objectId, "objectId");
        LinkedHashMap<String, String> out = new LinkedHashMap<>(
                normal.getOrDefault(object, Collections.emptyMap())
        );
        Map<String, Map<String, String>> byState = deltas.get(object);
        if (byState != null) {
            out.putAll(byState.getOrDefault(
                    StableId.require(stateId, "stateId"),
                    Collections.emptyMap()
            ));
        }
        return Collections.unmodifiableMap(out);
    }
}
