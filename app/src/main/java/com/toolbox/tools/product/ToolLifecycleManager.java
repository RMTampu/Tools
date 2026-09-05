package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolLifecycleManager {
    public enum State { COLD, LOADED, ACTIVE, FAILED, UNAVAILABLE }

    private final Map<String, State> states = new LinkedHashMap<>();
    private final Map<String, List<Runnable>> releaseHooks = new LinkedHashMap<>();
    private final Map<String, Integer> releaseCounts = new LinkedHashMap<>();

    public synchronized void register(String toolId) {
        String id = StableId.require(toolId, "toolId");
        states.put(id, State.COLD);
        releaseHooks.put(id, new ArrayList<>());
        releaseCounts.put(id, 0);
    }

    public synchronized void registerReleaseHook(
            String toolId,
            Runnable hook
    ) {
        String id = StableId.require(toolId, "toolId");
        if (!states.containsKey(id)) {
            throw new IllegalArgumentException("tool tidak terdaftar");
        }
        if (hook == null) throw new NullPointerException("hook");
        releaseHooks.get(id).add(hook);
    }

    public synchronized void load(String toolId) {
        transition(toolId, State.COLD, State.LOADED);
    }

    public synchronized void activate(String toolId) {
        String id = StableId.require(toolId, "toolId");
        State current = states.get(id);
        if (current == null || current == State.UNAVAILABLE) {
            throw new IllegalStateException("tool tidak tersedia");
        }
        if (current == State.FAILED) {
            throw new IllegalStateException("tool gagal dan harus dipulihkan");
        }
        List<String> activeOthers = new ArrayList<>();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            if (!entry.getKey().equals(id)
                    && entry.getValue() == State.ACTIVE) {
                activeOthers.add(entry.getKey());
            }
        }
        for (String active : activeOthers) {
            performRelease(active);
        }
        states.put(id, State.ACTIVE);
    }

    public synchronized void release(String toolId) {
        String id = StableId.require(toolId, "toolId");
        State current = states.get(id);
        if (current == State.ACTIVE || current == State.LOADED) {
            performRelease(id);
        }
    }

    public synchronized int releaseCount(String toolId) {
        String id = StableId.require(toolId, "toolId");
        Integer count = releaseCounts.get(id);
        if (count == null) {
            throw new IllegalArgumentException("tool tidak terdaftar");
        }
        return count;
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

    private void performRelease(String id) {
        try {
            for (Runnable hook : releaseHooks.getOrDefault(
                    id,
                    Collections.emptyList()
            )) {
                hook.run();
            }
            states.put(id, State.COLD);
            releaseCounts.put(
                    id,
                    releaseCounts.getOrDefault(id, 0) + 1
            );
        } catch (RuntimeException error) {
            states.put(id, State.FAILED);
            throw new IllegalStateException(
                    "release tool gagal: " + id,
                    error
            );
        }
    }

    private void transition(String toolId, State expected, State next) {
        String id = StableId.require(toolId, "toolId");
        if (states.get(id) != expected) throw new IllegalStateException("state tool tidak valid");
        states.put(id, next);
    }
}
