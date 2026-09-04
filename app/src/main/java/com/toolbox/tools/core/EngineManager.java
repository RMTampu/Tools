package com.toolbox.tools.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EngineManager {
    private final Map<String, EngineContract> engines = new LinkedHashMap<>();

    public synchronized void register(EngineContract engine) {
        Objects.requireNonNull(engine, "engine");
        if (!engine.isReady()) {
            throw new IllegalArgumentException("Engine is not ready: " + engine.id());
        }
        if (engines.containsKey(engine.id())) {
            throw new IllegalArgumentException("Duplicate engine id: " + engine.id());
        }
        engines.put(engine.id(), engine);
    }

    public synchronized boolean contains(String id) {
        return engines.containsKey(id);
    }

    public synchronized Map<String, EngineContract> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(engines));
    }
}
