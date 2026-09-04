package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class EventContract {
    private final String eventId;
    private final Set<String> compatibleActionTypes;

    public EventContract(String eventId, Set<String> compatibleActionTypes) {
        this.eventId = StableId.require(eventId, "eventId");
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (compatibleActionTypes != null) {
            for (String item : compatibleActionTypes) {
                copy.add(StableId.require(item, "compatibleActionType"));
            }
        }
        this.compatibleActionTypes = Collections.unmodifiableSet(copy);
    }

    public String eventId() { return eventId; }
    public Set<String> compatibleActionTypes() { return compatibleActionTypes; }
}
