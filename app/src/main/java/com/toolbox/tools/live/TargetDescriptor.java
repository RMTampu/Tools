package com.toolbox.tools.live;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class TargetDescriptor {
    private final String targetId;
    private final String labelIndonesia;
    private final boolean installed;
    private final boolean selfTarget;
    private final EditDoor editDoor;
    private final Map<CapabilityArea, CapabilityAvailability> declared;

    public TargetDescriptor(
            String targetId,
            String labelIndonesia,
            boolean installed,
            boolean selfTarget,
            EditDoor editDoor,
            Map<CapabilityArea, CapabilityAvailability> declared
    ) {
        this.targetId = StableId.require(targetId, "targetId");
        String label = Objects.requireNonNull(
                labelIndonesia,
                "labelIndonesia"
        ).trim();
        if (label.isEmpty() || label.length() > 120) {
            throw new IllegalArgumentException("target label invalid");
        }
        this.labelIndonesia = label;
        this.installed = installed;
        this.selfTarget = selfTarget;
        this.editDoor = Objects.requireNonNull(editDoor, "editDoor");

        EnumMap<CapabilityArea, CapabilityAvailability> values =
                new EnumMap<>(CapabilityArea.class);
        for (CapabilityArea area : CapabilityArea.values()) {
            CapabilityAvailability value = declared == null
                    ? CapabilityAvailability.UNAVAILABLE
                    : declared.get(area);
            values.put(
                    area,
                    value == null
                            ? CapabilityAvailability.UNAVAILABLE
                            : value
            );
        }
        this.declared = Collections.unmodifiableMap(values);
    }

    public String targetId() { return targetId; }
    public String labelIndonesia() { return labelIndonesia; }
    public boolean installed() { return installed; }
    public boolean selfTarget() { return selfTarget; }
    public EditDoor editDoor() { return editDoor; }
    public Map<CapabilityArea, CapabilityAvailability> declared() {
        return declared;
    }
}
