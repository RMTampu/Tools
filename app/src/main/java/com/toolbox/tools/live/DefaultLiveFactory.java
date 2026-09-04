package com.toolbox.tools.live;

import java.util.EnumMap;

public final class DefaultLiveFactory {
    private DefaultLiveFactory() {
    }

    public static TargetDescriptor selfTarget() {
        EnumMap<CapabilityArea, CapabilityAvailability> declared =
                new EnumMap<>(CapabilityArea.class);
        declared.put(CapabilityArea.UI, CapabilityAvailability.AVAILABLE);
        declared.put(CapabilityArea.LOGIC, CapabilityAvailability.AVAILABLE);
        declared.put(CapabilityArea.DATA, CapabilityAvailability.AVAILABLE);
        declared.put(CapabilityArea.BINDING, CapabilityAvailability.AVAILABLE);
        declared.put(CapabilityArea.ASSET, CapabilityAvailability.AVAILABLE);
        declared.put(CapabilityArea.RUNTIME, CapabilityAvailability.AVAILABLE);

        return new TargetDescriptor(
                "target.toolbox.self",
                "ToolBox Sendiri",
                true,
                true,
                EditDoor.DECLARATIVE,
                declared
        );
    }
}
