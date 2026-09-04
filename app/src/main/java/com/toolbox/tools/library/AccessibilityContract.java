package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;

public final class AccessibilityContract {
    private final String roleId;
    private final boolean labelRequired;
    private final boolean focusableByDefault;

    public AccessibilityContract(
            String roleId,
            boolean labelRequired,
            boolean focusableByDefault
    ) {
        this.roleId = StableId.require(roleId, "roleId");
        this.labelRequired = labelRequired;
        this.focusableByDefault = focusableByDefault;
    }

    public String roleId() { return roleId; }
    public boolean labelRequired() { return labelRequired; }
    public boolean focusableByDefault() { return focusableByDefault; }
}
