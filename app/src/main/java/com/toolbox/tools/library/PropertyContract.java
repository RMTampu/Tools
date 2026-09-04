package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class PropertyContract {
    private final String propertyId;
    private final PropertyType type;
    private final boolean nullable;
    private final boolean editable;
    private final String defaultValue;
    private final Set<String> enumValues;

    public PropertyContract(
            String propertyId,
            PropertyType type,
            boolean nullable,
            boolean editable,
            String defaultValue,
            Set<String> enumValues
    ) {
        this.propertyId = StableId.require(propertyId, "propertyId");
        this.type = Objects.requireNonNull(type, "type");
        this.nullable = nullable;
        this.editable = editable;
        this.defaultValue = defaultValue;
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (enumValues != null) copy.addAll(enumValues);
        if (type == PropertyType.ENUM && copy.isEmpty()) {
            throw new IllegalArgumentException("enum property requires values");
        }
        this.enumValues = Collections.unmodifiableSet(copy);
    }

    public String propertyId() { return propertyId; }
    public PropertyType type() { return type; }
    public boolean nullable() { return nullable; }
    public boolean editable() { return editable; }
    public String defaultValue() { return defaultValue; }
    public Set<String> enumValues() { return enumValues; }
}
