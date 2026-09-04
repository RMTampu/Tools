package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class DataFieldDefinition {
    private final String fieldId;
    private final ValueType type;
    private final boolean required;

    public DataFieldDefinition(
            String fieldId,
            ValueType type,
            boolean required
    ) {
        this.fieldId = StableId.require(fieldId, "fieldId");
        this.type = Objects.requireNonNull(type, "type");
        this.required = required;
    }

    public String fieldId() { return fieldId; }
    public ValueType type() { return type; }
    public boolean required() { return required; }
}
