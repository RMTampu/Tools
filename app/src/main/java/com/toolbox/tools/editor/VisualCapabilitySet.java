package com.toolbox.tools.editor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class VisualCapabilitySet {
    private final Set<VisualCapability> capabilities;

    public VisualCapabilitySet(Set<VisualCapability> capabilities) {
        EnumSet<VisualCapability> copy = capabilities == null
                ? EnumSet.noneOf(VisualCapability.class)
                : EnumSet.copyOf(capabilities);
        this.capabilities = Collections.unmodifiableSet(copy);
    }

    public static VisualCapabilitySet defaultEditable() {
        return new VisualCapabilitySet(EnumSet.allOf(VisualCapability.class));
    }

    public boolean supports(VisualCapability capability) {
        return capabilities.contains(capability);
    }

    public Set<VisualCapability> all() {
        return capabilities;
    }
}
