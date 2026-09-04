package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.library.ComponentDefinition;
import com.toolbox.tools.library.LibrarySearchResult;
import com.toolbox.tools.product.ScreenManager;
import com.toolbox.tools.runtime.BindingDefinition;
import com.toolbox.tools.runtime.DataFieldDefinition;
import com.toolbox.tools.runtime.DataSourceDefinition;
import com.toolbox.tools.runtime.FlowGraph;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class EditorPaneFactory {
    public interface SelectionCallback {
        void onSelected(String objectId);
    }

    private EditorPaneFactory() {}

    public static View visual(
            Context c,
            AppKernel kernel,
            AuthoringSection section,
            SelectionCallback selection
    ) {
        switch (section) {
            case UI:
                return ui(c, kernel, selection);
            case LOGIC:
                return logic(c, kernel);
            case DATA:
                return data(c, kernel);
            case BINDING:
                return binding(c, kernel);
            case ASSET:
                return asset(c, kernel);
            default:
                throw new IllegalStateException("fungsi editor tidak dikenal");
        }
    }

    public static View properties(
            Context c,
            AppKernel kernel,
            AuthoringSection section
    ) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 24)
        );
        scroll.addView(root);

        root.addView(UiKit.judul(c, "Properti Terstruktur", 18f));
        TextView info = UiKit.teks(
                c,
                "Properti membaca model yang sama dengan Visual dan Kode. "
                        + "Perubahan representabel tetap tersinkron dua arah.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        info.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 14));
        root.addView(info);

        switch (section) {
            case UI:
                propertyRow(root, c, "Objek dipilih", "Tombol Utama");
                propertyRow(root, c, "Lebar", "148 dp");
                propertyRow(root, c, "Tinggi", "46 dp");
                propertyRow(root, c, "Teks", "Buka Detail");
                propertyRow(root, c, "Warna", "Token Neon");
                propertyRow(root, c, "Radius", "14 dp");
                propertyRow(root, c, "Posisi", "Terikat Layout");
                propertyRow(root, c, "Aksesibilitas", "Tombol • berlabel");
                break;
            case LOGIC:
                propertyRow(root, c, "Alur aktif", "Saat Tombol Ditekan");
                propertyRow(root, c, "Node", "4");
                propertyRow(root, c, "Koneksi", "3");
                propertyRow(root, c, "Watchdog", "Aktif");
                propertyRow(root, c, "Batas loop", "Aman");
                break;
            case DATA:
                propertyRow(root, c, "Sumber", "data.items");
                propertyRow(root, c, "Kunci item", "field.id");
                propertyRow(root, c, "Paging", "20 item");
                propertyRow(root, c, "Data Contoh", "Aktif untuk pratinjau");
                break;
            case BINDING:
                propertyRow(root, c, "Profil", "Profil Pengikatan Bawaan");
                propertyRow(root, c, "Mode", "Satu arah");
                propertyRow(root, c, "Target ambigu", "Tidak ditebak");
                propertyRow(root, c, "Siklus", "Dicegah otomatis");
                break;
            case ASSET:
                propertyRow(root, c, "Tema", "Gelap Neon");
                propertyRow(root, c, "Aset bawaan", String.valueOf(
                        kernel.libraryManager().assets().allReady().size()
                ));
                propertyRow(root, c, "Komponen siap", String.valueOf(
                        kernel.libraryManager().components().allReady().size()
                ));
                propertyRow(root, c, "Template siap", String.valueOf(
                        kernel.libraryManager().templates().allReady().size()
                ));
                break;
        }
        return scroll;
    }

    public static View code(
            Context c,
            AppKernel kernel,
            AuthoringSection section
    ) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 24)
        );
        scroll.addView(root);

        root.addView(UiKit.judul(c, "Representasi Deklaratif", 18f));
        TextView note = UiKit.teks(
                c,
                "Kode adalah jalur cadangan lanjutan. ToolBox tidak mengeksekusi "
                        + "kode sembarang yang diunduh di host.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        note.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 12));
        root.addView(note);

        TextView code = UiKit.teks(
                c,
                codeText(kernel, section),
                11.5f,
                UiKit.TEKS
        );
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(true);
        code.setPadding(
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 14)
        );
        code.setBackground(UiKit.kartuPx(
                c,
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                16,
                1
        ));
        root.addView(code, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private static View ui(
            Context c,
            AppKernel kernel,
            SelectionCallback selection
    ) {
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12)
        );

        LinearLayout summary = UiKit.baris(c);
        summary.addView(statusChip(c, "Layar: 2", UiKit.NEON_BIRU));
        summary.addView(statusChip(c, "Komponen: "
                + kernel.libraryManager().components().allReady().size(), UiKit.NEON));
        summary.addView(statusChip(c, "Edit tanpa penyalinan", UiKit.NEON));
        root.addView(horizontal(c, summary));

        UiKit.ruang(root, c, 8);

        UiCanvasView canvas = new UiCanvasView(
                c,
                kernel,
                objectId -> {
                    if (selection != null) selection.onSelected(objectId);
                }
        );
        root.addView(canvas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        return root;
    }

    private static View logic(Context c, AppKernel kernel) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 20)
        );
        scroll.addView(root);

        root.addView(UiKit.judul(c, "Editor Logika Visual", 18f));
        TextView desc = UiKit.teks(
                c,
                "Hubungkan Event, Aksi, Kondisi, Variabel, dan Fungsi. "
                        + "Koneksi tidak kompatibel ditolak.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        desc.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 10));
        root.addView(desc);

        LogicGraphView graph = new LogicGraphView(c);
        root.addView(graph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 280)
        ));

        UiKit.ruang(root, c, 10);
        LinearLayout chips = UiKit.baris(c);
        chips.addView(statusChip(c, "Alur: "
                + kernel.runtimeEnvironment().model().flows().size(), UiKit.NEON));
        chips.addView(statusChip(c, "Pengawas aktif", UiKit.NEON_BIRU));
        chips.addView(statusChip(c, "Asinkron aman", UiKit.NEON_BIRU));
        root.addView(horizontal(c, chips));
        return scroll;
    }

    private static View data(Context c, AppKernel kernel) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 24)
        );
        scroll.addView(root);
        root.addView(UiKit.judul(c, "Editor Data", 18f));

        TextView desc = UiKit.teks(
                c,
                "Sumber • Koleksi • Tabel • Field • Relasi • Query • Data Contoh",
                11.5f,
                UiKit.TEKS_REDUP
        );
        desc.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 12));
        root.addView(desc);

        for (DataSourceDefinition source
                : kernel.runtimeEnvironment().model().dataSources().values()) {
            LinearLayout card = card(c);
            card.addView(UiKit.labelBagian(c, "SUMBER DATA  •  " + source.sourceId()));
            for (Map.Entry<String, DataFieldDefinition> entry
                    : source.fields().entrySet()) {
                LinearLayout row = UiKit.baris(c);
                TextView name = UiKit.judul(c, entry.getKey(), 12.5f);
                row.addView(name, new LinearLayout.LayoutParams(
                        0, UiKit.dp(c, 36), 1
                ));
                TextView type = UiKit.chip(
                        c,
                        entry.getValue().type().name(),
                        false
                );
                row.addView(type);
                card.addView(row);
            }
            UiKit.ruang(card, c, 8);
            LinearLayout actions = UiKit.baris(c);
            actions.addView(UiKit.tombol(c, "+ Tambah Field", false),
                    new LinearLayout.LayoutParams(0, UiKit.dp(c, 42), 1));
            actions.addView(UiKit.tombol(c, "Data Contoh", false),
                    new LinearLayout.LayoutParams(0, UiKit.dp(c, 42), 1));
            card.addView(actions);
            root.addView(card);
            UiKit.ruang(root, c, 10);
        }
        return scroll;
    }

    private static View binding(Context c, AppKernel kernel) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 24)
        );
        scroll.addView(root);

        LinearLayout header = UiKit.baris(c);
        TextView title = UiKit.judul(c, "Pusat Pengikatan", 18f);
        header.addView(title, new LinearLayout.LayoutParams(0, UiKit.dp(c, 44), 1));
        TextView auto = UiKit.tombol(c, "Hubungkan Semua Otomatis", true);
        header.addView(auto);
        root.addView(header);

        TextView desc = UiKit.teks(
                c,
                "Target hanya dihubungkan jika deterministik dan kompatibel. "
                        + "Ambiguitas menghasilkan diagnostik, bukan tebakan.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        desc.setPadding(0, 0, 0, UiKit.dp(c, 12));
        root.addView(desc);

        for (BindingDefinition binding
                : kernel.runtimeEnvironment().model().bindings().values()) {
            LinearLayout card = card(c);
            LinearLayout row = UiKit.baris(c);
            TextView name = UiKit.judul(c, binding.bindingId(), 13f);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, UiKit.dp(c, 36), 1
            ));
            row.addView(UiKit.chip(c, "TERHUBUNG", true));
            card.addView(row);

            TextView detail = UiKit.teks(
                    c,
                    binding.sourceId() + "." + binding.sourceFieldId()
                            + "  →  " + binding.targetInstanceId()
                            + "." + binding.targetPropertyId(),
                    11f,
                    UiKit.TEKS_REDUP
            );
            card.addView(detail);
            root.addView(card);
            UiKit.ruang(root, c, 10);
        }

        LinearLayout audit = card(c);
        audit.addView(UiKit.labelBagian(c, "AUDIT"));
        audit.addView(UiKit.teks(c, "Masalah: 0", 12f, UiKit.NEON));
        audit.addView(UiKit.teks(c, "Siklus ditekan: 0", 12f, UiKit.TEKS_REDUP));
        audit.addView(UiKit.teks(c, "Riwayat perubahan tersedia", 12f, UiKit.TEKS_REDUP));
        root.addView(audit);
        return scroll;
    }

    private static View asset(Context c, AppKernel kernel) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 24)
        );
        scroll.addView(root);

        root.addView(UiKit.judul(c, "Pusat Aset & Library", 18f));
        TextView desc = UiKit.teks(
                c,
                "Gambar • Ikon • Font • Animasi • Template • Komponen • Tema • Token • Impor",
                11.5f,
                UiKit.TEKS_REDUP
        );
        desc.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 12));
        root.addView(desc);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(2);
        String[][] cards = new String[][]{
                {"Komponen", String.valueOf(kernel.libraryManager().components().allReady().size())},
                {"Aset", String.valueOf(kernel.libraryManager().assets().allReady().size())},
                {"Template", String.valueOf(kernel.libraryManager().templates().allReady().size())},
                {"Tema", "Gelap Neon"},
                {"Token", String.valueOf(kernel.productServices().themes().snapshot().size())},
                {"Impor", "Validasi Aman"}
        };
        for (String[] item : cards) {
            LinearLayout card = card(c);
            TextView t = UiKit.judul(c, item[0], 13f);
            card.addView(t);
            TextView v = UiKit.judul(c, item[1], 18f);
            v.setTextColor(UiKit.NEON);
            card.addView(v);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0;
            gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gp.setMargins(
                    UiKit.dp(c, 4), UiKit.dp(c, 4),
                    UiKit.dp(c, 4), UiKit.dp(c, 4)
            );
            grid.addView(card, gp);
        }
        root.addView(grid);

        UiKit.ruang(root, c, 12);
        root.addView(UiKit.labelBagian(c, "KOMPONEN BAWAAN"));
        int shown = 0;
        for (ComponentDefinition item
                : kernel.libraryManager().components().allReady()) {
            if (shown++ >= 10) break;
            LinearLayout row = UiKit.baris(c);
            TextView label = UiKit.teks(
                    c,
                    item.labelIndonesia(),
                    12.5f,
                    UiKit.TEKS
            );
            row.addView(label, new LinearLayout.LayoutParams(
                    0, UiKit.dp(c, 38), 1
            ));
            row.addView(UiKit.chip(c, "SIAP", true));
            root.addView(row);
        }

        return scroll;
    }

    private static String codeText(
            AppKernel kernel,
            AuthoringSection section
    ) {
        switch (section) {
            case UI:
                return "{\n"
                        + "  \"screenId\": \"screen.home\",\n"
                        + "  \"mode\": \"responsive\",\n"
                        + "  \"component\": \"component.button\",\n"
                        + "  \"text\": \"Buka Detail\",\n"
                        + "  \"theme\": \"asset.theme.dark.neon\"\n"
                        + "}";
            case LOGIC:
                return "{\n"
                        + "  \"flowId\": \"flow.home.click\",\n"
                        + "  \"event\": \"event.home.click\",\n"
                        + "  \"condition\": \"data.valid\",\n"
                        + "  \"action\": \"action.navigation.open\"\n"
                        + "}";
            case DATA:
                return "{\n"
                        + "  \"sourceId\": \"data.items\",\n"
                        + "  \"key\": \"field.id\",\n"
                        + "  \"paging\": 20,\n"
                        + "  \"fields\": [\"field.id\", \"field.title\"]\n"
                        + "}";
            case BINDING:
                return "{\n"
                        + "  \"bindingId\": \"binding.home.title\",\n"
                        + "  \"source\": \"data.items.field.title\",\n"
                        + "  \"target\": \"instance.home.primary.property.text\",\n"
                        + "  \"mode\": \"ONE_WAY\"\n"
                        + "}";
            case ASSET:
                return "{\n"
                        + "  \"theme\": \"asset.theme.dark.neon\",\n"
                        + "  \"assetsReady\": "
                        + kernel.libraryManager().assets().allReady().size()
                        + ",\n  \"componentsReady\": "
                        + kernel.libraryManager().components().allReady().size()
                        + "\n}";
            default:
                return "{}";
        }
    }

    private static void propertyRow(
            LinearLayout root,
            Context c,
            String label,
            String value
    ) {
        LinearLayout row = UiKit.baris(c);
        row.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 8),
                UiKit.dp(c, 12),
                UiKit.dp(c, 8)
        );
        row.setBackground(UiKit.kartuPx(
                c,
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                12,
                1
        ));
        TextView l = UiKit.teks(c, label, 12f, UiKit.TEKS_REDUP);
        row.addView(l, new LinearLayout.LayoutParams(
                0, UiKit.dp(c, 38), 1
        ));
        TextView v = UiKit.judul(c, value, 12f);
        v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(v, new LinearLayout.LayoutParams(
                0, UiKit.dp(c, 38), 1
        ));
        root.addView(row);
        UiKit.ruang(root, c, 6);
    }

    private static LinearLayout card(Context c) {
        LinearLayout card = UiKit.kolom(c);
        card.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12),
                UiKit.dp(c, 12)
        );
        card.setBackground(UiKit.kartuPx(
                c,
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                16,
                1
        ));
        return card;
    }

    private static TextView statusChip(Context c, String text, int color) {
        TextView v = UiKit.teks(c, text, 10.5f, color);
        v.setPadding(
                UiKit.dp(c, 9),
                UiKit.dp(c, 5),
                UiKit.dp(c, 9),
                UiKit.dp(c, 5)
        );
        v.setBackground(UiKit.kartuPx(
                c,
                UiKit.PERMUKAAN,
                color,
                999,
                1
        ));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        p.rightMargin = UiKit.dp(c, 6);
        v.setLayoutParams(p);
        return v;
    }

    private static View horizontal(Context c, LinearLayout child) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(child);
        return h;
    }
}
