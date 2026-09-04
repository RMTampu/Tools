package com.toolbox.tools.live;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CapabilityScanResult {
    private final String targetId;
    private final boolean installed;
    private final Map<CapabilityArea, CapabilityAvailability> statuses;

    public CapabilityScanResult(
            String targetId,
            boolean installed,
            Map<CapabilityArea, CapabilityAvailability> statuses
    ) {
        this.targetId = targetId;
        this.installed = installed;
        EnumMap<CapabilityArea, CapabilityAvailability> copy =
                new EnumMap<>(CapabilityArea.class);
        copy.putAll(statuses);
        this.statuses = Collections.unmodifiableMap(copy);
    }

    public String targetId() { return targetId; }
    public boolean installed() { return installed; }

    public CapabilityAvailability status(CapabilityArea area) {
        CapabilityAvailability value = statuses.get(area);
        return value == null
                ? CapabilityAvailability.UNAVAILABLE
                : value;
    }

    public Map<CapabilityArea, CapabilityAvailability> statuses() {
        return statuses;
    }

    public boolean liveAvailable() {
        return installed
                && status(CapabilityArea.RUNTIME)
                == CapabilityAvailability.AVAILABLE;
    }
}
