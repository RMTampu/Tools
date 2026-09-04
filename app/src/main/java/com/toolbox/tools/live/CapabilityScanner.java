package com.toolbox.tools.live;

import java.util.EnumMap;
import java.util.Objects;

public final class CapabilityScanner {
    public CapabilityScanResult scan(TargetDescriptor target) {
        Objects.requireNonNull(target, "target");
        EnumMap<CapabilityArea, CapabilityAvailability> out =
                new EnumMap<>(CapabilityArea.class);

        for (CapabilityArea area : CapabilityArea.values()) {
            CapabilityAvailability declared =
                    target.declared().get(area);

            if (!target.installed()) {
                out.put(area, CapabilityAvailability.UNAVAILABLE);
                continue;
            }

            if (declared == CapabilityAvailability.AVAILABLE
                    && target.editDoor() == EditDoor.NONE) {
                out.put(
                        area,
                        area == CapabilityArea.RUNTIME
                                ? CapabilityAvailability.UNAVAILABLE
                                : CapabilityAvailability.READ_ONLY
                );
                continue;
            }

            out.put(area, declared);
        }

        return new CapabilityScanResult(
                target.targetId(),
                target.installed(),
                out
        );
    }
}
