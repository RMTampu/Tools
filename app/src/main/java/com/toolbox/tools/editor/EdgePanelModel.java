package com.toolbox.tools.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EdgePanelModel {
    private final String titleIndonesia;
    private final String breadcrumb;
    private final List<EdgeItem> items;

    public EdgePanelModel(
            String titleIndonesia,
            String breadcrumb,
            List<EdgeItem> items
    ) {
        this.titleIndonesia = requireText(
                titleIndonesia,
                "titleIndonesia"
        );
        this.breadcrumb = requireText(
                breadcrumb,
                "breadcrumb"
        );
        this.items = Collections.unmodifiableList(
                new ArrayList<>(
                        Objects.requireNonNull(items, "items")
                )
        );
    }

    private static String requireText(String value, String field) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isEmpty() || text.length() > 120) {
            throw new IllegalArgumentException(field + " invalid");
        }
        return text;
    }

    public String titleIndonesia() { return titleIndonesia; }
    public String breadcrumb() { return breadcrumb; }
    public List<EdgeItem> items() { return items; }
}
