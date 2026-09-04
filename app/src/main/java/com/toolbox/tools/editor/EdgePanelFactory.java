package com.toolbox.tools.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EdgePanelFactory {
    public EdgePanelModel create(
            EditorFunction function,
            boolean editEnabled,
            boolean hasSelection,
            VisualCapabilitySet capabilities
    ) {
        if (!editEnabled) {
            return new EdgePanelModel(
                    "Editor",
                    "Edit NONAKTIF",
                    quickAccessItems()
            );
        }

        switch (function) {
            case UI:
                return hasSelection
                        ? selectedUi(capabilities)
                        : addToScreen();
            case LOGIC:
                return contextual(
                        "Logic",
                        "Editor / Logic",
                        "Event",
                        "Action",
                        "Condition",
                        "Flow",
                        "Variable",
                        "Function"
                );
            case DATA:
                return contextual(
                        "Data",
                        "Editor / Data",
                        "Source",
                        "Collection",
                        "Table",
                        "Field",
                        "Relation",
                        "Query",
                        "Mock Data"
                );
            case BINDING:
                return contextual(
                        "Binding",
                        "Editor / Binding",
                        "Auto Connect",
                        "Status",
                        "Issues",
                        "Map",
                        "Usage",
                        "History"
                );
            case ASSET:
                return contextual(
                        "Asset",
                        "Editor / Asset",
                        "Category",
                        "Import",
                        "Preview",
                        "Usage",
                        "Compatibility",
                        "Dependency"
                );
            default:
                throw new IllegalStateException("unknown editor function");
        }
    }

    private static EdgePanelModel addToScreen() {
        return contextual(
                "Tambah ke Layar",
                "Editor / UI",
                "Komponen",
                "Template",
                "Kit",
                "Asset",
                "Recent",
                "Favorite"
        );
    }

    private static EdgePanelModel selectedUi(
            VisualCapabilitySet capabilities
    ) {
        List<EdgeItem> items = new ArrayList<>();
        add(items, capabilities, VisualCapability.STYLE, "edge.style", "Style");
        add(items, capabilities, VisualCapability.SIZE, "edge.size", "Size");
        add(items, capabilities, VisualCapability.POSITION, "edge.position", "Position");
        add(items, capabilities, VisualCapability.CONTENT, "edge.content", "Content");
        add(items, capabilities, VisualCapability.COLOR, "edge.color", "Color");
        add(items, capabilities, VisualCapability.SPACING, "edge.spacing", "Spacing");
        add(items, capabilities, VisualCapability.SHAPE, "edge.shape", "Shape");
        add(items, capabilities, VisualCapability.BORDER, "edge.border", "Border");
        add(items, capabilities, VisualCapability.FONT_TEXT, "edge.font", "Font/Text");
        add(items, capabilities, VisualCapability.OPACITY, "edge.opacity", "Opacity");
        add(items, capabilities, VisualCapability.TRANSFORM, "edge.transform", "Rotation/Transform");
        add(items, capabilities, VisualCapability.ALIGNMENT, "edge.alignment", "Alignment");
        add(items, capabilities, VisualCapability.LAYER, "edge.layer", "Layer");
        add(items, capabilities, VisualCapability.STATE, "edge.state", "State");
        add(items, capabilities, VisualCapability.ANIMATION, "edge.animation", "Animation");
        add(items, capabilities, VisualCapability.AUTO_CONNECT_BINDING, "edge.autoconnect", "Auto Connect Binding");
        add(items, capabilities, VisualCapability.EVENT_ACTION, "edge.eventaction", "Event/Action");
        add(items, capabilities, VisualCapability.ACCESSIBILITY, "edge.accessibility", "Accessibility");
        add(items, capabilities, VisualCapability.LOCK, "edge.lock", "Lock");
        add(items, capabilities, VisualCapability.OTHERS, "edge.others", "Others");
        return new EdgePanelModel(
                "Edit Object",
                "Editor / UI / Object",
                items
        );
    }

    private static void add(
            List<EdgeItem> items,
            VisualCapabilitySet capabilities,
            VisualCapability capability,
            String id,
            String label
    ) {
        if (capabilities.supports(capability)) {
            items.add(new EdgeItem(id, label, true));
        }
    }

    private static EdgePanelModel contextual(
            String title,
            String breadcrumb,
            String... labels
    ) {
        List<EdgeItem> items = new ArrayList<>();
        for (String label : labels) {
            String id = "edge." + label.toLowerCase(java.util.Locale.ROOT)
                    .replace(" ", ".")
                    .replace("/", ".");
            items.add(new EdgeItem(id, label, true));
        }
        return new EdgePanelModel(title, breadcrumb, items);
    }

    private static List<EdgeItem> quickAccessItems() {
        List<EdgeItem> items = new ArrayList<>();
        items.add(new EdgeItem("quick.edit", "Edit ON / OFF", true));
        items.add(new EdgeItem("quick.tool", "Tool", true));
        items.add(new EdgeItem("quick.settings", "Pengaturan", true));
        items.add(new EdgeItem("quick.floating", "Floating Window", true));
        return Collections.unmodifiableList(items);
    }
}
