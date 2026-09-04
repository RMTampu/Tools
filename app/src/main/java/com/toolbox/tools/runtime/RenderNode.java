package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RenderNode {
    private final String instanceId;
    private final String componentId;
    private final Map<String, String> properties;
    private final boolean available;

    public RenderNode(
            String instanceId,
            String componentId,
            Map<String, String> properties,
            boolean available
    ) {
        this.instanceId = StableId.require(instanceId, "instanceId");
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
        this.available = available;
    }

    public String instanceId() { return instanceId; }
    public String componentId() { return componentId; }
    public Map<String, String> properties() { return properties; }
    public boolean available() { return available; }
}
