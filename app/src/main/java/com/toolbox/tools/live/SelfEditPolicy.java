package com.toolbox.tools.live;

public final class SelfEditPolicy {
    public boolean isProtected(String resourceId) {
        return resourceId.startsWith("kernel.")
                || resourceId.startsWith("recovery.")
                || resourceId.startsWith("safety.")
                || resourceId.startsWith("security.");
    }

    public boolean isDeclarativeEditable(String resourceId) {
        if (isProtected(resourceId)) {
            return false;
        }
        return resourceId.startsWith("screen.")
                || resourceId.startsWith("ui.")
                || resourceId.startsWith("style.")
                || resourceId.startsWith("data.")
                || resourceId.startsWith("binding.")
                || resourceId.startsWith("asset.")
                || resourceId.startsWith("flow.")
                || resourceId.startsWith("action.")
                || resourceId.startsWith("event.");
    }

    public void requireEditable(String resourceId) {
        if (!isDeclarativeEditable(resourceId)) {
            throw new IllegalArgumentException(
                    isProtected(resourceId)
                            ? "SELF_EDIT_PROTECTED_CORE"
                            : "SELF_EDIT_NON_DECLARATIVE_SURFACE"
            );
        }
    }
}
