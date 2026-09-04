package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DataSourceDefinition {
    private final String sourceId;
    private final String stableItemKeyFieldId;
    private final Map<String, DataFieldDefinition> fields;

    public DataSourceDefinition(
            String sourceId,
            String stableItemKeyFieldId,
            List<DataFieldDefinition> fields
    ) {
        this.sourceId = StableId.require(sourceId, "sourceId");
        this.stableItemKeyFieldId = StableId.require(
                stableItemKeyFieldId,
                "stableItemKeyFieldId"
        );
        LinkedHashMap<String, DataFieldDefinition> copy = new LinkedHashMap<>();
        for (DataFieldDefinition field : fields == null
                ? Collections.<DataFieldDefinition>emptyList()
                : fields) {
            Objects.requireNonNull(field, "field");
            if (copy.put(field.fieldId(), field) != null) {
                throw new IllegalArgumentException("duplicate data field");
            }
        }
        if (!copy.containsKey(this.stableItemKeyFieldId)) {
            throw new IllegalArgumentException("stable item key field missing");
        }
        this.fields = Collections.unmodifiableMap(copy);
    }

    public String sourceId() { return sourceId; }
    public String stableItemKeyFieldId() { return stableItemKeyFieldId; }
    public Map<String, DataFieldDefinition> fields() { return fields; }
}
