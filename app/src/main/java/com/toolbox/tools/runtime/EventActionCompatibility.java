package com.toolbox.tools.runtime;

public final class EventActionCompatibility {
    public boolean isCompatible(
            EventDefinition event,
            ActionContract action
    ) {
        if (event == null || action == null) return false;
        for (java.util.Map.Entry<String, ValueType> input : action.inputs().entrySet()) {
            ValueType available = event.payload().get(input.getKey());
            if (available == null || available != input.getValue()) {
                return false;
            }
        }
        return true;
    }
}
