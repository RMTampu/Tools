package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class VisualEditOperation {
    private final String objectId;
    private final VisualCapability capability;
    private final String propertyId;
    private final String value;

    public VisualEditOperation(
            String objectId,
            VisualCapability capability,
            String propertyId,
            String value
    ) {
        this.objectId = StableId.require(objectId, "objectId");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.propertyId = StableId.require(propertyId, "propertyId");
        this.value = Objects.requireNonNull(value, "value");
    }

    public String objectId() { return objectId; }
    public VisualCapability capability() { return capability; }
    public String propertyId() { return propertyId; }
    public String value() { return value; }
}
