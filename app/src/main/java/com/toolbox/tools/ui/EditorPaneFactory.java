package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import java.util.Collections;
import java.util.LinkedHashMap;
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
                "Representasi Properti mengedit Project Store yang sama dengan Visual dan Kode. "
                        + "Perubahan langsung menjadi working state dan tetap menunggu Simpan manual.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        info.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 12));
        root.addView(info);

        final String key = editableResourceKey(section);
        final String fallback = editableFallback(section);
        String current = kernel.projectManager().current().resources().getOrDefault(
                key,
                fallback
        );

        propertyRow(root, c, "Kunci Properti", key);

        EditText value = new EditText(c);
        value.setSingleLine(false);
        value.setText(current);
        value.setTextColor(UiKit.TEKS);
        value.setHintTextColor(UiKit.TEKS_REDUP);
        value.setHint("Nilai properti");
        value.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 10),
                UiKit.dp(c, 12),
                UiKit.dp(c, 10)
        );
        value.setBackground(UiKit.kartuPx(
                c,
                UiKit.PERMUKAAN,
                UiKit.NEON_BIRU,
                12,
                1
        ));
        root.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 86)
        ));

        UiKit.ruang(root, c, 8);
        TextView status = UiKit.teks(
                c,
                "Status: sinkron dengan working state",
                11f,
                UiKit.TEKS_REDUP
        );
        root.addView(status);

        TextView apply = UiKit.tombol(c, "Terapkan Properti", true);
        apply.setOnClickListener(v -> {
            String next = value.getText().toString();
            if (next.trim().isEmpty()) {
                status.setText("Ditolak: nilai tidak boleh kosong");
                return;
            }
            try {
                LinkedHashMap<String, String> upserts = new LinkedHashMap<>();
                upserts.put(key, next);
                kernel.projectManager().applyResourceTransaction(
                        upserts,
                        Collections.emptySet()
                );
                status.setText("Diterapkan • belum disimpan");
            } catch (RuntimeException error) {
                status.setText("Ditolak aman • " + error.getClass().getSimpleName());
            }
        });
        root.addView(apply);

        UiKit.ruang(root, c, 12);
        propertyRow(
                root,
                c,
                "Revisi Tersimpan",
                String.valueOf(kernel.projectManager().savedRevision())
        );
        propertyRow(
                root,
                c,
                "Perubahan Tertunda",
                kernel.projectManager().hasUnsavedChanges() ? "YA" : "TIDAK"
        );
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
                "Kode ini deklaratif, bukan arbitrary executable code. Format: satu resourceId=value per baris. "
                        + "Hanya namespace fungsi aktif yang diterima.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        note.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 12));
        root.addView(note);

        final String key = editableResourceKey(section);
        final String fallback = editableFallback(section);
        final String prefix = editablePrefix(section);
        String current = kernel.projectManager().current().resources().getOrDefault(
                key,
                fallback
        );

        EditText code = new EditText(c);
        code.setText(key + "=" + current);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextColor(UiKit.TEKS);
        code.setHintTextColor(UiKit.TEKS_REDUP);
        code.setGravity(Gravity.TOP | Gravity.START);
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
                UiKit.dp(c, 220)
        ));

        TextView status = UiKit.teks(
                c,
                "Status: belum ada perubahan kode",
                11f,
                UiKit.TEKS_REDUP
        );
        root.addView(status);

        TextView apply = UiKit.tombol(c, "Terapkan Kode Deklaratif", true);
        apply.setOnClickListener(v -> {
            try {
                LinkedHashMap<String, String> upserts = new LinkedHashMap<>();
                String[] lines = code.getText().toString().split("\\n");
                for (String raw : lines) {
                    String line = raw.trim();
                    if (line.isEmpty()) continue;
                    int split = line.indexOf('=');
                    if (split <= 0 || split == line.length() - 1) {
                        throw new IllegalArgumentException("format baris tidak valid");
                    }
                    String resourceId = line.substring(0, split).trim();
                    String value = line.substring(split + 1);
                    if (!resourceId.startsWith(prefix)) {
                        throw new IllegalArgumentException(
                                "namespace tidak sesuai fungsi aktif"
                        );
                    }
                    upserts.put(resourceId, value);
                }
                if (upserts.isEmpty()) {
                    throw new IllegalArgumentException("tidak ada perubahan");
                }
                kernel.projectManager().applyResourceTransaction(
                        upserts,
                        Collections.emptySet()
                );
                status.setText("Diterapkan • Visual/Properti membaca working state yang sama");
            } catch (RuntimeException error) {
                status.setText("Ditolak aman • " + error.getMessage());
            }
        });
        root.addView(apply);
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
                type.setClickable(false);
                type.setFocusable(false);
                row.addView(type);
                card.addView(row);
            }
            UiKit.ruang(card, c, 8);
            TextView panelHint = UiKit.teks(
                    c,
                    "Tambah kolom, kueri, relasi, dan data contoh dikelola dari Edge Panel.",
                    10.5f,
                    UiKit.TEKS_REDUP
            );
            card.addView(panelHint);
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
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 44)
        ));
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
            TextView connected = UiKit.chip(c, "TERHUBUNG", true);
            connected.setClickable(false);
            connected.setFocusable(false);
            row.addView(connected);
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
            TextView ready = UiKit.chip(c, "SIAP", true);
            ready.setClickable(false);
            ready.setFocusable(false);
            row.addView(ready);
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

    private static String editableResourceKey(AuthoringSection section) {
        switch (section) {
            case UI: return "ui.object.home.primary.text";
            case LOGIC: return "logic.ui.home.primary.action";
            case DATA: return "data.editor.query";
            case BINDING: return "binding.ui.home.primary.mode";
            case ASSET: return "asset.editor.active";
            default: return "config.editor.value";
        }
    }

    private static String editableFallback(AuthoringSection section) {
        switch (section) {
            case UI: return "Buka Detail";
            case LOGIC: return "open.detail";
            case DATA: return "all";
            case BINDING: return "auto";
            case ASSET: return "asset.theme.dark.neon";
            default: return "default";
        }
    }

    private static String editablePrefix(AuthoringSection section) {
        switch (section) {
            case UI: return "ui.";
            case LOGIC: return "logic.";
            case DATA: return "data.";
            case BINDING: return "binding.";
            case ASSET: return "asset.";
            default: return "config.";
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
