package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CompositeComponentSpec {
    private final String compositeId;
    private final Map<String, ComponentInstance> children;

    public CompositeComponentSpec(
            String compositeId,
            Map<String, ComponentInstance> children
    ) {
        this.compositeId = StableId.require(compositeId, "compositeId");
        LinkedHashMap<String, ComponentInstance> copy = new LinkedHashMap<>();
        if (children != null) {
            for (Map.Entry<String, ComponentInstance> entry : children.entrySet()) {
                String internalId = StableId.require(entry.getKey(), "internalChildId");
                ComponentInstance child = Objects.requireNonNull(
                        entry.getValue(),
                        "child"
                );
                if (copy.put(internalId, child) != null) {
                    throw new IllegalArgumentException("duplicate internal child id");
                }
            }
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("composite requires children");
        }
        this.children = Collections.unmodifiableMap(copy);
    }

    public boolean isCompatible(ComponentRegistry registry) {
        for (ComponentInstance child : children.values()) {
            if (!child.isAvailable(registry)) return false;
        }
        return true;
    }

    public String compositeId() { return compositeId; }
    public Map<String, ComponentInstance> children() { return children; }
}
