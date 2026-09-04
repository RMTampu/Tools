package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ComponentVariant {
    private final String variantId;
    private final String baseComponentId;
    private final VersionNumber baseVersion;
    private final Map<String, String> propertyDeltas;

    public ComponentVariant(
            String variantId,
            String baseComponentId,
            VersionNumber baseVersion,
            Map<String, String> propertyDeltas
    ) {
        this.variantId = StableId.require(variantId, "variantId");
        this.baseComponentId = StableId.require(baseComponentId, "baseComponentId");
        this.baseVersion = Objects.requireNonNull(baseVersion, "baseVersion");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (propertyDeltas != null) {
            for (Map.Entry<String, String> entry : propertyDeltas.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "propertyId"),
                        Objects.requireNonNull(entry.getValue(), "propertyDelta")
                );
            }
        }
        this.propertyDeltas = Collections.unmodifiableMap(copy);
    }

    public boolean isCompatible(ComponentRegistry registry) {
        ComponentDefinition base = registry.resolveExact(baseComponentId, baseVersion);
        if (base == null) return false;
        return base.properties().keySet().containsAll(propertyDeltas.keySet());
    }

    public String variantId() { return variantId; }
    public String baseComponentId() { return baseComponentId; }
    public VersionNumber baseVersion() { return baseVersion; }
    public Map<String, String> propertyDeltas() { return propertyDeltas; }
}
