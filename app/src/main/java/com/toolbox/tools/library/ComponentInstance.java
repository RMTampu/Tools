package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ComponentInstance {
    private final String instanceId;
    private final String componentId;
    private final VersionNumber componentVersion;
    private final Map<String, String> propertyOverrides;

    public ComponentInstance(
            String instanceId,
            String componentId,
            VersionNumber componentVersion,
            Map<String, String> propertyOverrides
    ) {
        this.instanceId = StableId.require(instanceId, "instanceId");
        this.componentId = StableId.require(componentId, "componentId");
        this.componentVersion = Objects.requireNonNull(
                componentVersion,
                "componentVersion"
        );
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (propertyOverrides != null) {
            for (Map.Entry<String, String> entry : propertyOverrides.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "propertyId"),
                        Objects.requireNonNull(entry.getValue(), "property override")
                );
            }
        }
        this.propertyOverrides = Collections.unmodifiableMap(copy);
    }

    public boolean isAvailable(ComponentRegistry registry) {
        return registry.resolveExact(componentId, componentVersion) != null;
    }

    public String instanceId() { return instanceId; }
    public String componentId() { return componentId; }
    public VersionNumber componentVersion() { return componentVersion; }
    public Map<String, String> propertyOverrides() { return propertyOverrides; }
}
