package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class AccessibilityContract {
    public enum Semantic {
        ENABLED,
        SELECTED,
        CHECKED,
        REQUIRED,
        EXPANDED,
        ERROR,
        LOADING
    }

    private final String roleId;
    private final boolean labelRequired;
    private final boolean descriptionSupported;
    private final boolean focusableByDefault;
    private final int focusOrder;
    private final Set<Semantic> supportedSemantics;

    public AccessibilityContract(
            String roleId,
            boolean labelRequired,
            boolean focusableByDefault
    ) {
        this(
                roleId,
                labelRequired,
                true,
                focusableByDefault,
                0,
                EnumSet.allOf(Semantic.class)
        );
    }

    public AccessibilityContract(
            String roleId,
            boolean labelRequired,
            boolean descriptionSupported,
            boolean focusableByDefault,
            int focusOrder,
            Set<Semantic> supportedSemantics
    ) {
        this.roleId = StableId.require(
                roleId,
                "roleId"
        );
        this.labelRequired = labelRequired;
        this.descriptionSupported = descriptionSupported;
        this.focusableByDefault = focusableByDefault;
        if (focusOrder < 0 || focusOrder > 10_000) {
            throw new IllegalArgumentException(
                    "focusOrder invalid"
            );
        }
        this.focusOrder = focusOrder;
        this.supportedSemantics = Collections.unmodifiableSet(
                supportedSemantics == null
                        || supportedSemantics.isEmpty()
                        ? EnumSet.noneOf(Semantic.class)
                        : EnumSet.copyOf(supportedSemantics)
        );
    }

    public String roleId() { return roleId; }
    public boolean labelRequired() { return labelRequired; }
    public boolean descriptionSupported() {
        return descriptionSupported;
    }
    public boolean focusableByDefault() {
        return focusableByDefault;
    }
    public int focusOrder() { return focusOrder; }
    public Set<Semantic> supportedSemantics() {
        return supportedSemantics;
    }

    public boolean validate(
            boolean interactive,
            boolean iconOnly,
            String accessibleLabel,
            String accessibleDescription,
            Set<Semantic> semantics
    ) {
        String label = accessibleLabel == null
                ? ""
                : accessibleLabel.trim();
        String description =
                accessibleDescription == null
                        ? ""
                        : accessibleDescription.trim();

        if ((labelRequired || interactive || iconOnly)
                && label.isEmpty()) {
            return false;
        }
        if (!description.isEmpty()
                && !descriptionSupported) {
            return false;
        }
        if (semantics != null
                && !supportedSemantics.containsAll(
                        semantics
                )) {
            return false;
        }
        return !focusableByDefault
                || !interactive
                || focusOrder >= 0;
    }

    public boolean completeContract() {
        return roleId != null
                && supportedSemantics != null
                && supportedSemantics.contains(
                        Semantic.ENABLED
                )
                && supportedSemantics.contains(
                        Semantic.ERROR
                )
                && supportedSemantics.contains(
                        Semantic.LOADING
                );
    }
}
