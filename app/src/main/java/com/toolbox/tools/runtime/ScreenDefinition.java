package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import com.toolbox.tools.library.ComponentInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ScreenDefinition {
    private final String screenId;
    private final String labelIndonesia;
    private final List<ComponentInstance> components;

    public ScreenDefinition(
            String screenId,
            String labelIndonesia,
            List<ComponentInstance> components
    ) {
        this.screenId = StableId.require(screenId, "screenId");
        String label = Objects.requireNonNull(labelIndonesia, "labelIndonesia").trim();
        if (label.isEmpty() || label.length() > 120) {
            throw new IllegalArgumentException("screen label invalid");
        }
        this.labelIndonesia = label;
        this.components = Collections.unmodifiableList(
                components == null
                        ? new ArrayList<>()
                        : new ArrayList<>(components)
        );
    }

    public String screenId() { return screenId; }
    public String labelIndonesia() { return labelIndonesia; }
    public List<ComponentInstance> components() { return components; }
}
