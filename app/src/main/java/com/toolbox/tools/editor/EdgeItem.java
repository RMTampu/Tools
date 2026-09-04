package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class EdgeItem {
    private final String itemId;
    private final String labelIndonesia;
    private final boolean enabled;

    public EdgeItem(
            String itemId,
            String labelIndonesia,
            boolean enabled
    ) {
        this.itemId = StableId.require(itemId, "itemId");
        String label = Objects.requireNonNull(
                labelIndonesia,
                "labelIndonesia"
        ).trim();
        if (label.isEmpty() || label.length() > 80) {
            throw new IllegalArgumentException("edge label invalid");
        }
        this.labelIndonesia = label;
        this.enabled = enabled;
    }

    public String itemId() { return itemId; }
    public String labelIndonesia() { return labelIndonesia; }
    public boolean enabled() { return enabled; }
}
