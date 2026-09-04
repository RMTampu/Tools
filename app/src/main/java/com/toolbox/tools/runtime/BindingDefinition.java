package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class BindingDefinition {
    private final String bindingId;
    private final String sourceId;
    private final String sourceFieldId;
    private final String targetInstanceId;
    private final String targetPropertyId;
    private final ValueType valueType;
    private final BindingMode mode;

    public BindingDefinition(
            String bindingId,
            String sourceId,
            String sourceFieldId,
            String targetInstanceId,
            String targetPropertyId,
            ValueType valueType,
            BindingMode mode
    ) {
        this.bindingId = StableId.require(bindingId, "bindingId");
        this.sourceId = StableId.require(sourceId, "sourceId");
        this.sourceFieldId = StableId.require(sourceFieldId, "sourceFieldId");
        this.targetInstanceId = StableId.require(targetInstanceId, "targetInstanceId");
        this.targetPropertyId = StableId.require(targetPropertyId, "targetPropertyId");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public String bindingId() { return bindingId; }
    public String sourceId() { return sourceId; }
    public String sourceFieldId() { return sourceFieldId; }
    public String targetInstanceId() { return targetInstanceId; }
    public String targetPropertyId() { return targetPropertyId; }
    public ValueType valueType() { return valueType; }
    public BindingMode mode() { return mode; }
}
