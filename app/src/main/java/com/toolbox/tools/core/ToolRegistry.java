package com.toolbox.tools.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ToolRegistry {
    private final Map<String, ToolDescriptor> tools = new LinkedHashMap<>();

    public synchronized void register(ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (tools.containsKey(descriptor.id())) {
            throw new IllegalArgumentException("Duplicate tool id: " + descriptor.id());
        }
        tools.put(descriptor.id(), descriptor);
    }

    public synchronized boolean contains(String id) {
        return tools.containsKey(id);
    }

    public synchronized int size() {
        return tools.size();
    }

    public synchronized Collection<ToolDescriptor> all() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(tools.values()));
    }
}
