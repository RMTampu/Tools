package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class VisualObjectState {
    private final String objectId;
    private final String componentId;
    private final Map<String, String> properties;

    public VisualObjectState(
            String objectId,
            String componentId,
            Map<String, String> properties
    ) {
        this.objectId = StableId.require(objectId, "objectId");
        this.componentId = StableId.require(componentId, "componentId");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "propertyId"),
                        Objects.requireNonNull(entry.getValue(), "property value")
                );
            }
        }
        this.properties = Collections.unmodifiableMap(copy);
    }

    public String objectId() { return objectId; }
    public String componentId() { return componentId; }
    public Map<String, String> properties() { return properties; }

    public VisualObjectState withProperty(String propertyId, String value) {
        LinkedHashMap<String, String> next = new LinkedHashMap<>(properties);
        next.put(
                StableId.require(propertyId, "propertyId"),
                Objects.requireNonNull(value, "value")
        );
        return new VisualObjectState(objectId, componentId, next);
    }
}
