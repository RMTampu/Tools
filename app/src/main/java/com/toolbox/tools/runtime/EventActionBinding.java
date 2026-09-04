package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;

public final class EventActionBinding {
    private final String bindingId;
    private final String eventId;
    private final String actionId;

    public EventActionBinding(
            String bindingId,
            String eventId,
            String actionId
    ) {
        this.bindingId = StableId.require(bindingId, "bindingId");
        this.eventId = StableId.require(eventId, "eventId");
        this.actionId = StableId.require(actionId, "actionId");
    }

    public String bindingId() { return bindingId; }
    public String eventId() { return eventId; }
    public String actionId() { return actionId; }
}
