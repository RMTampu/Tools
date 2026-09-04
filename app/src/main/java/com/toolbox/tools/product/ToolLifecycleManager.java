package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolLifecycleManager {
    public enum State { COLD, LOADED, ACTIVE, FAILED, UNAVAILABLE }

    private final Map<String, State> states = new LinkedHashMap<>();

    public synchronized void register(String toolId) {
        states.put(StableId.require(toolId, "toolId"), State.COLD);
    }

    public synchronized void load(String toolId) {
        transition(toolId, State.COLD, State.LOADED);
    }

    public synchronized void activate(String toolId) {
        String id = StableId.require(toolId, "toolId");
        State current = states.get(id);
        if (current != State.LOADED && current != State.ACTIVE) {
            throw new IllegalStateException("tool belum dimuat");
        }
        for (Map.Entry<String, State> entry : states.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue() == State.ACTIVE) {
                entry.setValue(State.LOADED);
            }
        }
        states.put(id, State.ACTIVE);
    }

    public synchronized void release(String toolId) {
        String id = StableId.require(toolId, "toolId");
        State current = states.get(id);
        if (current == State.ACTIVE || current == State.LOADED) {
            states.put(id, State.COLD);
        }
    }

    public synchronized void fail(String toolId) {
        states.put(StableId.require(toolId, "toolId"), State.FAILED);
    }

    public synchronized void markUnavailable(String toolId) {
        states.put(StableId.require(toolId, "toolId"), State.UNAVAILABLE);
    }

    public synchronized Map<String, State> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    public synchronized int activeCount() {
        int count = 0;
        for (State state : states.values()) if (state == State.ACTIVE) count++;
        return count;
    }

    private void transition(String toolId, State expected, State next) {
        String id = StableId.require(toolId, "toolId");
        if (states.get(id) != expected) throw new IllegalStateException("state tool tidak valid");
        states.put(id, next);
    }
}
