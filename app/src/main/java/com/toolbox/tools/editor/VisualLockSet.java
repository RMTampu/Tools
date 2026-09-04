package com.toolbox.tools.editor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class VisualLockSet {
    private final Set<VisualCapability> locked =
            EnumSet.noneOf(VisualCapability.class);

    public synchronized void setLocked(
            VisualCapability capability,
            boolean value
    ) {
        if (value) locked.add(capability);
        else locked.remove(capability);
    }

    public synchronized boolean isLocked(VisualCapability capability) {
        return locked.contains(capability);
    }

    public synchronized Set<VisualCapability> snapshot() {
        return Collections.unmodifiableSet(EnumSet.copyOf(locked));
    }
}
