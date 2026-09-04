package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PermissionManager {
    private final Set<String> required = new LinkedHashSet<>();
    private final Set<String> granted = new LinkedHashSet<>();

    public synchronized void require(String permissionId) {
        required.add(StableId.require(permissionId, "permissionId"));
    }

    public synchronized void setGranted(String permissionId, boolean value) {
        String id = StableId.require(permissionId, "permissionId");
        if (value) granted.add(id);
        else granted.remove(id);
    }

    public synchronized Set<String> missing() {
        LinkedHashSet<String> out = new LinkedHashSet<>(required);
        out.removeAll(granted);
        return Collections.unmodifiableSet(out);
    }

    public synchronized boolean isReady() {
        return missing().isEmpty();
    }
}
