package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionRegistry {
    private final Map<String, ActionContract> actions = new LinkedHashMap<>();

    public synchronized void register(ActionContract contract) {
        String id = StableId.require(contract.actionId(), "actionId");
        if (actions.put(id, contract) != null) {
            throw new IllegalArgumentException("action already registered");
        }
    }

    public synchronized ActionContract resolve(String actionId) {
        return actions.get(StableId.require(actionId, "actionId"));
    }

    public synchronized Map<String, ActionContract> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(actions));
    }
}
