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
                        "Logika",
                        "Editor / Logika",
                        "Peristiwa",
                        "Aksi",
                        "Kondisi",
                        "Alur",
                        "Variabel",
                        "Fungsi"
                );
            case DATA:
                return contextual(
                        "Data",
                        "Editor / Data",
                        "Sumber",
                        "Koleksi",
                        "Tabel",
                        "Kolom Data",
                        "Relasi",
                        "Kueri",
                        "Data Contoh"
                );
            case BINDING:
                return contextual(
                        "Pengikatan",
                        "Editor / Pengikatan",
                        "Hubungkan Otomatis",
                        "Status",
                        "Masalah",
                        "Peta",
                        "Penggunaan",
                        "Riwayat"
                );
            case ASSET:
                return contextual(
                        "Aset",
                        "Editor / Aset",
                        "Kategori",
                        "Impor",
                        "Pratinjau",
                        "Penggunaan",
                        "Kompatibilitas",
                        "Dependensi"
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
                "Aset",
                "Terbaru",
                "Favorit"
        );
    }

    private static EdgePanelModel selectedUi(
            VisualCapabilitySet capabilities
    ) {
        List<EdgeItem> items = new ArrayList<>();
        add(items, capabilities, VisualCapability.STYLE, "edge.style", "Gaya");
        add(items, capabilities, VisualCapability.SIZE, "edge.size", "Ukuran");
        add(items, capabilities, VisualCapability.POSITION, "edge.position", "Posisi");
        add(items, capabilities, VisualCapability.CONTENT, "edge.content", "Konten");
        add(items, capabilities, VisualCapability.COLOR, "edge.color", "Warna");
        add(items, capabilities, VisualCapability.SPACING, "edge.spacing", "Spasi");
        add(items, capabilities, VisualCapability.SHAPE, "edge.shape", "Bentuk");
        add(items, capabilities, VisualCapability.BORDER, "edge.border", "Garis Tepi");
        add(items, capabilities, VisualCapability.FONT_TEXT, "edge.font", "Font/Teks");
        add(items, capabilities, VisualCapability.OPACITY, "edge.opacity", "Opasitas");
        add(items, capabilities, VisualCapability.TRANSFORM, "edge.transform", "Rotasi/Transformasi");
        add(items, capabilities, VisualCapability.ALIGNMENT, "edge.alignment", "Perataan");
        add(items, capabilities, VisualCapability.LAYER, "edge.layer", "Lapisan");
        add(items, capabilities, VisualCapability.STATE, "edge.state", "Keadaan");
        add(items, capabilities, VisualCapability.ANIMATION, "edge.animation", "Animasi");
        add(items, capabilities, VisualCapability.AUTO_CONNECT_BINDING, "edge.autoconnect", "Hubungkan Pengikatan Otomatis");
        add(items, capabilities, VisualCapability.EVENT_ACTION, "edge.eventaction", "Peristiwa/Aksi");
        add(items, capabilities, VisualCapability.ACCESSIBILITY, "edge.accessibility", "Aksesibilitas");
        add(items, capabilities, VisualCapability.LOCK, "edge.lock", "Kunci");
        add(items, capabilities, VisualCapability.OTHERS, "edge.others", "Lainnya");
        return new EdgePanelModel(
                "Edit Objek",
                "Editor / UI / Objek",
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
        items.add(new EdgeItem("quick.edit", "Edit AKTIF / NONAKTIF", true));
        items.add(new EdgeItem("quick.tool", "Alat", true));
        items.add(new EdgeItem("quick.settings", "Pengaturan", true));
        items.add(new EdgeItem("quick.floating", "Jendela Mengambang", true));
        return Collections.unmodifiableList(items);
    }
}
