package com.toolbox.tools.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryDataSource {
    private final DataSourceDefinition definition;
    private final Map<String, DataRecord> records = new LinkedHashMap<>();

    public InMemoryDataSource(DataSourceDefinition definition) {
        this.definition = definition;
    }

    public synchronized void put(DataRecord record) {
        if (records.put(record.stableKey(), record) != null) {
            throw new IllegalArgumentException("duplicate stable data-item key");
        }
    }

    public synchronized List<DataRecord> query(PagedQuery query) {
        List<DataRecord> all = new ArrayList<>(records.values());
        int from = Math.min(query.offset(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return Collections.unmodifiableList(
                new ArrayList<>(all.subList(from, to))
        );
    }

    public DataSourceDefinition definition() { return definition; }
}
