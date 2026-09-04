package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class StateContract {
    private final Set<String> stateIds;

    public StateContract(Set<String> stateIds) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (stateIds != null) {
            for (String id : stateIds) {
                copy.add(StableId.require(id, "stateId"));
            }
        }
        if (!copy.contains("state.normal")) {
            throw new IllegalArgumentException("component state contract requires state.normal");
        }
        this.stateIds = Collections.unmodifiableSet(copy);
    }

    public Set<String> stateIds() { return stateIds; }
}
