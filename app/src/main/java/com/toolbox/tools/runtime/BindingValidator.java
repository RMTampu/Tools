package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentDefinition;
import com.toolbox.tools.library.ComponentInstance;
import com.toolbox.tools.library.ComponentRegistry;
import com.toolbox.tools.library.PropertyContract;
import com.toolbox.tools.library.PropertyType;

public final class BindingValidator {
    public boolean isCompatible(
            BindingDefinition binding,
            DataSourceDefinition source,
            ComponentInstance instance,
            ComponentRegistry components
    ) {
        if (binding == null || source == null || instance == null) return false;
        if (!binding.sourceId().equals(source.sourceId())) return false;
        if (!binding.targetInstanceId().equals(instance.instanceId())) return false;
        DataFieldDefinition field = source.fields().get(binding.sourceFieldId());
        if (field == null || field.type() != binding.valueType()) return false;

        ComponentDefinition component = components.resolveExact(
                instance.componentId(),
                instance.componentVersion()
        );
        if (component == null) return false;
        PropertyContract property = component.properties().get(
                binding.targetPropertyId()
        );
        if (property == null) return false;
        return compatible(property.type(), binding.valueType());
    }

    private static boolean compatible(PropertyType property, ValueType value) {
        switch (property) {
            case BOOLEAN: return value == ValueType.BOOLEAN;
            case NUMBER:
            case DIMENSION: return value == ValueType.NUMBER;
            case TEXT:
            case COLOR:
            case ENUM:
            case URI:
            case ASSET: return value == ValueType.TEXT
                    || value == ValueType.REFERENCE;
            case LIST: return value == ValueType.LIST;
            case OBJECT: return value == ValueType.OBJECT;
            case REFERENCE: return value == ValueType.REFERENCE;
            default: return false;
        }
    }
}
