package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EventDefinition {
    private final String eventId;
    private final Map<String, ValueType> payload;

    public EventDefinition(String eventId, Map<String, ValueType> payload) {
        this.eventId = StableId.require(eventId, "eventId");
        LinkedHashMap<String, ValueType> copy = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, ValueType> entry : payload.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "payloadId"),
                        Objects.requireNonNull(entry.getValue(), "payloadType")
                );
            }
        }
        this.payload = Collections.unmodifiableMap(copy);
    }

    public String eventId() { return eventId; }
    public Map<String, ValueType> payload() { return payload; }
}
