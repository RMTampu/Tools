package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.editor.EdgeItem;
import com.toolbox.tools.editor.EdgePanelModel;
import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.product.FullProductVerifier;
import com.toolbox.tools.product.FreezeEngine;
import com.toolbox.tools.repair.HealthReport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WorkspaceShellView extends FrameLayout {
    private enum Representation {
        VISUAL, PROPERTI, KODE
    }

    private final AppKernel kernel;
    private final LinearLayout topActions;
    private final LinearLayout representationBar;
    private final LinearLayout runtimeModeBar;
    private final FrameLayout workspace;
    private final LinearLayout bottomTools;
    private final FrameLayout edgeContainer;
    private final LinearLayout edgeContent;
    private final TextView edgeHandle;
    private final TextView bubble;
    private final FrameLayout overlayLayer;
    private final TextView statusLine;

    private AuthoringSection active = AuthoringSection.UI;
    private Representation representation = Representation.VISUAL;
    private boolean edgeOpen = true;
    private boolean bubbleDragging;
    private float bubbleDownX;
    private float bubbleDownY;
    private float bubbleStartX;
    private float bubbleStartY;

    public WorkspaceShellView(Context context, AppKernel kernel) {
        super(context);
        this.kernel = kernel;
        setBackgroundColor(UiKit.LATAR);
        setClipChildren(false);

        LinearLayout body = UiKit.kolom(context);
        body.setBackgroundColor(UiKit.LATAR);
        addView(body, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        topActions = UiKit.baris(context);
        topActions.setPadding(
                UiKit.dp(context, 12),
                UiKit.dp(context, 8),
                UiKit.dp(context, 12),
                UiKit.dp(context, 6)
        );
        body.addView(topActions, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 58)
        ));

        LinearLayout titleBlock = UiKit.kolom(context);
        TextView title = UiKit.judul(context, "ToolBox", 21f);
        title.setTextColor(UiKit.NEON);
        TextView subtitle = UiKit.teks(
                context,
                "Proyek Demo • Produk Penuh • Bahasa Indonesia",
                10.5f,
                UiKit.TEKS_REDUP
        );
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        topActions.addView(titleBlock, new LinearLayout.LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                1
        ));

        TextView simpan = actionSmall("Simpan");
        simpan.setOnClickListener(v -> saveProject());
        topActions.addView(simpan);

        TextView batal = actionSmall("Urungkan");
        batal.setOnClickListener(v -> undo());
        topActions.addView(batal);

        TextView ulangi = actionSmall("Ulangi");
        ulangi.setOnClickListener(v -> redo());
        topActions.addView(ulangi);

        TextView alat = actionSmall("Alat");
        alat.setOnClickListener(v -> showTools());
        topActions.addView(alat);

        representationBar = UiKit.baris(context);
        representationBar.setPadding(
                UiKit.dp(context, 10), 0,
                UiKit.dp(context, 10), 0
        );
        body.addView(horizontal(representationBar), new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 44)
        ));

        runtimeModeBar = UiKit.baris(context);
        runtimeModeBar.setPadding(
                UiKit.dp(context, 10), 0,
                UiKit.dp(context, 10), 0
        );
        body.addView(horizontal(runtimeModeBar), new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 42)
        ));

        statusLine = UiKit.teks(
                context,
                "Siap • 5 engine aktif • autosave nonaktif",
                10.5f,
                UiKit.TEKS_REDUP
        );
        statusLine.setPadding(
                UiKit.dp(context, 14),
                0,
                UiKit.dp(context, 14),
                UiKit.dp(context, 4)
        );
        body.addView(statusLine, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 28)
        ));

        workspace = new FrameLayout(context);
        workspace.setBackgroundColor(UiKit.LATAR);
        body.addView(workspace, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        bottomTools = UiKit.baris(context);
        bottomTools.setGravity(Gravity.CENTER);
        bottomTools.setPadding(
                UiKit.dp(context, 8),
                UiKit.dp(context, 5),
                UiKit.dp(context, 8),
                UiKit.dp(context, 7)
        );
        bottomTools.setBackground(UiKit.kartuPx(
                context,
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                0,
                1
        ));
        body.addView(bottomTools, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 64)
        ));

        edgeContainer = new FrameLayout(context);
        edgeContainer.setBackground(UiKit.kartuPx(
                context,
                Color.rgb(8, 24, 31),
                UiKit.GARIS,
                18,
                1
        ));
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(
                UiKit.dp(context, 150),
                LayoutParams.MATCH_PARENT,
                Gravity.END
        );
        ep.topMargin = UiKit.dp(context, 144);
        ep.bottomMargin = UiKit.dp(context, 68);
        ep.rightMargin = UiKit.dp(context, 4);
        addView(edgeContainer, ep);

        ScrollView edgeScroll = new ScrollView(context);
        edgeContent = UiKit.kolom(context);
        edgeContent.setPadding(
                UiKit.dp(context, 10),
                UiKit.dp(context, 10),
                UiKit.dp(context, 10),
                UiKit.dp(context, 14)
        );
        edgeScroll.addView(edgeContent);
        edgeContainer.addView(edgeScroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        edgeHandle = UiKit.judul(context, "‹", 22f);
        edgeHandle.setGravity(Gravity.CENTER);
        edgeHandle.setTextColor(UiKit.NEON);
        edgeHandle.setBackground(UiKit.kartuPx(
                context,
                UiKit.PERMUKAAN_2,
                UiKit.NEON,
                12,
                1
        ));
        edgeHandle.setOnClickListener(v -> toggleEdge());
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                UiKit.dp(context, 34),
                UiKit.dp(context, 52),
                Gravity.CENTER_VERTICAL | Gravity.START
        );
        hp.leftMargin = -UiKit.dp(context, 28);
        edgeContainer.addView(edgeHandle, hp);

        overlayLayer = new FrameLayout(context);
        overlayLayer.setVisibility(GONE);
        addView(overlayLayer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        bubble = UiKit.judul(context, "TB", 14f);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(UiKit.LATAR);
        bubble.setBackground(circle(UiKit.NEON));
        bubble.setElevation(UiKit.dp(context, 10));
        bubble.setOnTouchListener(this::handleBubbleTouch);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                UiKit.dp(context, 54),
                UiKit.dp(context, 54),
                Gravity.END | Gravity.BOTTOM
        );
        bp.rightMargin = UiKit.dp(context, 22);
        bp.bottomMargin = UiKit.dp(context, 82);
        addView(bubble, bp);

        kernel.editorEnvironment().shell().setLiveCapability(true);
        renderRepresentationBar();
        renderRuntimeModeBar();
        renderToolBar();
        renderWorkspace();
        renderEdge();
    }

    private TextView actionSmall(String text) {
        TextView v = UiKit.tombol(getContext(), text, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                UiKit.dp(getContext(), 38)
        );
        p.leftMargin = UiKit.dp(getContext(), 5);
        v.setLayoutParams(p);
        return v;
    }

    private void renderRepresentationBar() {
        representationBar.removeAllViews();
        representationBar.addView(modeChip(
                "Visual",
                representation == Representation.VISUAL,
                () -> setRepresentation(Representation.VISUAL)
        ));
        representationBar.addView(modeChip(
                "Properti",
                representation == Representation.PROPERTI,
                () -> setRepresentation(Representation.PROPERTI)
        ));
        representationBar.addView(modeChip(
                "Kode",
                representation == Representation.KODE,
                () -> setRepresentation(Representation.KODE)
        ));
        TextView divider = UiKit.teks(
                getContext(),
                "   •   Model sama • Sinkron dua arah aman",
                10.5f,
                UiKit.TEKS_REDUP
        );
        representationBar.addView(divider);
    }

    private void renderRuntimeModeBar() {
        runtimeModeBar.removeAllViews();
        EditorMode mode = kernel.editorEnvironment().shell().mode();
        runtimeModeBar.addView(runtimeChip("Edit", mode == EditorMode.EDIT, EditorMode.EDIT));
        runtimeModeBar.addView(runtimeChip(
                "Pratinjau",
                mode == EditorMode.PREVIEW,
                EditorMode.PREVIEW
        ));
        runtimeModeBar.addView(runtimeChip("Uji", mode == EditorMode.TEST, EditorMode.TEST));
        runtimeModeBar.addView(runtimeChip(
                "Langsung",
                mode == EditorMode.LIVE,
                EditorMode.LIVE
        ));

        TextView edit = UiKit.chip(
                getContext(),
                kernel.editorEnvironment().shell().editEnabled()
                        ? "Edit ON"
                        : "Edit OFF",
                kernel.editorEnvironment().shell().editEnabled()
        );
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                UiKit.dp(getContext(), 32)
        );
        ep.leftMargin = UiKit.dp(getContext(), 8);
        edit.setLayoutParams(ep);
        edit.setOnClickListener(v -> {
            boolean next = !kernel.editorEnvironment().shell().editEnabled();
            kernel.editorEnvironment().shell().setEditEnabled(next);
            renderRuntimeModeBar();
            renderWorkspace();
            renderEdge();
        });
        runtimeModeBar.addView(edit);
    }

    private void renderToolBar() {
        bottomTools.removeAllViews();
        addTool("UI", AuthoringSection.UI);
        addTool("Logika", AuthoringSection.LOGIC);
        addTool("Data", AuthoringSection.DATA);
        addTool("Pengikatan", AuthoringSection.BINDING);
        addTool("Aset", AuthoringSection.ASSET);
    }

    private void addTool(String label, AuthoringSection section) {
        boolean selected = active == section;
        TextView v = UiKit.teks(
                getContext(),
                label,
                11.5f,
                selected ? UiKit.NEON : UiKit.TEKS_REDUP
        );
        v.setGravity(Gravity.CENTER);
        v.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
        ));
        v.setBackground(UiKit.kartuPx(
                getContext(),
                selected ? Color.rgb(8, 55, 49) : Color.TRANSPARENT,
                selected ? UiKit.NEON : Color.TRANSPARENT,
                14,
                selected ? 1 : 0
        ));
        v.setOnClickListener(view -> {
            active = section;
            kernel.authoringWorkspace().activate(section);
            kernel.productServices().resources().activate(section);
            kernel.editorEnvironment().shell().clearSelection();
            renderToolBar();
            renderWorkspace();
            renderEdge();
            updateStatus();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                1
        );
        p.setMargins(
                UiKit.dp(getContext(), 2),
                UiKit.dp(getContext(), 1),
                UiKit.dp(getContext(), 2),
                UiKit.dp(getContext(), 1)
        );
        bottomTools.addView(v, p);
    }

    private void renderWorkspace() {
        workspace.removeAllViews();
        View pane;
        switch (representation) {
            case PROPERTI:
                pane = EditorPaneFactory.properties(
                        getContext(),
                        kernel,
                        active
                );
                break;
            case KODE:
                pane = EditorPaneFactory.code(
                        getContext(),
                        kernel,
                        active
                );
                break;
            case VISUAL:
            default:
                pane = EditorPaneFactory.visual(
                        getContext(),
                        kernel,
                        active,
                        objectId -> {
                            renderEdge();
                            showFloatingEditor("Edit Objek", Arrays.asList(
                                    "Gaya",
                                    "Ukuran",
                                    "Posisi",
                                    "Konten",
                                    "Warna",
                                    "Spasi",
                                    "Aksesibilitas",
                                    "Kunci"
                            ));
                        }
                );
                break;
        }
        workspace.addView(pane, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    private void renderEdge() {
        edgeContent.removeAllViews();
        EdgePanelModel model = kernel.editorEnvironment().shell().edgePanel(
                VisualCapabilitySet.defaultEditable()
        );

        TextView crumb = UiKit.teks(
                getContext(),
                translate(model.breadcrumb()),
                9.5f,
                UiKit.TEKS_REDUP
        );
        edgeContent.addView(crumb);

        TextView title = UiKit.judul(
                getContext(),
                translate(model.titleIndonesia()),
                13f
        );
        title.setTextColor(UiKit.NEON_BIRU);
        title.setPadding(0, UiKit.dp(getContext(), 4), 0, UiKit.dp(getContext(), 8));
        edgeContent.addView(title);

        for (EdgeItem item : model.items()) {
            String label = translate(item.labelIndonesia());
            TextView v = UiKit.tombol(getContext(), label, false);
            v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            v.setTextSize(11f);
            v.setOnClickListener(view -> showFloatingEditor(
                    label,
                    editorOptions(label)
            ));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    UiKit.dp(getContext(), 40)
            );
            p.bottomMargin = UiKit.dp(getContext(), 5);
            edgeContent.addView(v, p);
        }
    }

    private void setRepresentation(Representation next) {
        representation = next;
        renderRepresentationBar();
        renderWorkspace();
        updateStatus();
    }

    private void setRuntimeMode(EditorMode mode) {
        try {
            kernel.editorEnvironment().shell().setMode(mode);
            renderRuntimeModeBar();
            renderWorkspace();
            renderEdge();
            updateStatus();
        } catch (RuntimeException error) {
            toast("Mode Langsung belum tersedia untuk target ini.");
        }
    }

    private TextView modeChip(
            String label,
            boolean selected,
            Runnable action
    ) {
        TextView v = UiKit.chip(getContext(), label, selected);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                UiKit.dp(getContext(), 32)
        );
        p.rightMargin = UiKit.dp(getContext(), 6);
        v.setLayoutParams(p);
        v.setOnClickListener(view -> action.run());
        return v;
    }

    private TextView runtimeChip(
            String label,
            boolean selected,
            EditorMode mode
    ) {
        return modeChip(label, selected, () -> setRuntimeMode(mode));
    }

    private void toggleEdge() {
        edgeOpen = !edgeOpen;
        edgeContainer.animate()
                .translationX(edgeOpen ? 0 : UiKit.dp(getContext(), 136))
                .setDuration(180)
                .start();
        edgeHandle.setText(edgeOpen ? "‹" : "›");
    }

    private boolean handleBubbleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                bubbleDownX = event.getRawX();
                bubbleDownY = event.getRawY();
                bubbleStartX = view.getX();
                bubbleStartY = view.getY();
                bubbleDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - bubbleDownX;
                float dy = event.getRawY() - bubbleDownY;
                if (Math.abs(dx) > UiKit.dp(getContext(), 5)
                        || Math.abs(dy) > UiKit.dp(getContext(), 5)) {
                    bubbleDragging = true;
                }
                float maxX = Math.max(0, getWidth() - view.getWidth());
                float maxY = Math.max(0, getHeight() - view.getHeight());
                view.setX(Math.max(0, Math.min(maxX, bubbleStartX + dx)));
                view.setY(Math.max(0, Math.min(maxY, bubbleStartY + dy)));
                return true;
            case MotionEvent.ACTION_UP:
                if (!bubbleDragging) showBubbleMenu();
                return true;
            default:
                return false;
        }
    }

    private void showBubbleMenu() {
        List<String> rows = new ArrayList<>();
        rows.add(kernel.editorEnvironment().shell().editEnabled()
                ? "Edit: AKTIF"
                : "Edit: NONAKTIF");
        rows.add("Alat");
        rows.add("Pengaturan");
        rows.add("Jendela Mengambang");
        rows.add("Reset Shell");
        showCommandOverlay("Akses Cepat", rows, value -> {
            if (value.startsWith("Edit:")) {
                kernel.editorEnvironment().shell().setEditEnabled(
                        !kernel.editorEnvironment().shell().editEnabled()
                );
                closeOverlay();
                renderRuntimeModeBar();
                renderWorkspace();
                renderEdge();
            } else if ("Alat".equals(value)) {
                closeOverlay();
                showTools();
            } else if ("Pengaturan".equals(value)) {
                closeOverlay();
                showSettings();
            } else if ("Reset Shell".equals(value)) {
                kernel.editorEnvironment().shell().emergencyReset();
                closeOverlay();
                active = AuthoringSection.UI;
                representation = Representation.VISUAL;
                renderRepresentationBar();
                renderRuntimeModeBar();
                renderToolBar();
                renderWorkspace();
                renderEdge();
            } else {
                closeOverlay();
                showFloatingEditor(
                        "Jendela Mengambang",
                        Arrays.asList(
                                "Konteks aktif: " + labelSection(active),
                                "Representasi: " + labelRepresentation(),
                                "Mode: " + labelEditorMode()
                        )
                );
            }
        });
    }

    private void showTools() {
        showCommandOverlay(
                "Alat ToolBox",
                Arrays.asList(
                        "Proyek Tersimpan",
                        "Aplikasi Terinstal",
                        "Edit ToolBox",
                        "Buat / Edit Komponen",
                        "Pemulihan & Backup",
                        "Freeze / Mode Simpan",
                        "Perbaikan & Kesehatan",
                        "Paket Evolusi Tanpa Rebuild",
                        "Build & READY",
                        "Diagnostik",
                        "Pengaturan"
                ),
                value -> {
                    closeOverlay();
                    if ("Pengaturan".equals(value)) {
                        showSettings();
                    } else if ("Freeze / Mode Simpan".equals(value)) {
                        showFreeze();
                    } else if ("Perbaikan & Kesehatan".equals(value)) {
                        showHealth();
                    } else if ("Build & READY".equals(value)) {
                        showBuild();
                    } else if ("Diagnostik".equals(value)) {
                        showDiagnostics();
                    } else if ("Pemulihan & Backup".equals(value)) {
                        showRecovery();
                    } else if ("Paket Evolusi Tanpa Rebuild".equals(value)) {
                        showEvolution();
                    } else if ("Aplikasi Terinstal".equals(value)) {
                        showInstalledTarget();
                    } else if ("Edit ToolBox".equals(value)) {
                        showSelfEdit();
                    } else if ("Buat / Edit Komponen".equals(value)) {
                        active = AuthoringSection.UI;
                        renderToolBar();
                        renderWorkspace();
                        showFloatingEditor(
                                "Editor Komponen",
                                Arrays.asList(
                                        "Master / Instance",
                                        "Variant",
                                        "Composite",
                                        "Detach / Rebase",
                                        "Uji Komponen"
                                )
                        );
                    } else {
                        showFloatingEditor(
                                value,
                                Arrays.asList(
                                        "project.default",
                                        "Revisi: " + kernel.projectManager().savedRevision(),
                                        kernel.projectManager().hasUnsavedChanges()
                                                ? "Ada perubahan belum disimpan"
                                                : "Semua perubahan tersimpan"
                                )
                        );
                    }
                }
        );
    }

    private void showSettings() {
        showCommandOverlay(
                "Pengaturan",
                Arrays.asList(
                        "Bahasa: Bahasa Indonesia",
                        "Tema: Gelap Neon",
                        "Representasi Default: Visual",
                        "Autosave: NONAKTIF",
                        "Simpan: Manual Transaksional",
                        "Satu fungsi berat aktif",
                        "Target Host: Android 11 / API 30 / arm64-v8a"
                ),
                value -> {}
        );
    }

    private void showFreeze() {
        FreezeEngine freeze = kernel.productServices().freeze();
        List<String> rows = new ArrayList<>();
        rows.add("Status: " + freezeStateIndonesia(freeze.state()));
        rows.add("Mode Normal");
        rows.add("Mode Simpan • Titik Pemeriksaan");
        rows.add("Mode Simpan • Pemulihan");
        rows.add("Commit Baseline Kerja");
        rows.add("Thaw / Kembali Normal");
        showCommandOverlay("Freeze & Mode Simpan", rows, value -> {
            try {
                if ("Mode Simpan • Titik Pemeriksaan".equals(value)) {
                    if (freeze.state() == FreezeEngine.State.NORMAL) {
                        freeze.freeze();
                    } else if (freeze.state() == FreezeEngine.State.FROZEN) {
                        freeze.commit();
                    }
                    toast("Titik pemeriksaan aktif.");
                } else if ("Mode Simpan • Pemulihan".equals(value)) {
                    if (freeze.state() == FreezeEngine.State.FROZEN) {
                        freeze.recover();
                        toast("Pemulihan selesai.");
                    }
                } else if ("Commit Baseline Kerja".equals(value)) {
                    if (freeze.state() == FreezeEngine.State.FROZEN) {
                        freeze.commit();
                        toast("Baseline kerja diperbarui.");
                    }
                } else if ("Thaw / Kembali Normal".equals(value)
                        || "Mode Normal".equals(value)) {
                    if (freeze.state() == FreezeEngine.State.FROZEN) {
                        freeze.thaw();
                    }
                    toast("Mode normal aktif.");
                }
                closeOverlay();
                updateStatus();
            } catch (Exception error) {
                toast("Operasi Freeze gagal aman.");
            }
        });
    }

    private void showHealth() {
        HealthReport report = kernel.healthMonitor().inspect(kernel);
        showCommandOverlay(
                "Kesehatan & Perbaikan",
                Arrays.asList(
                        "Status: " + (report.isHealthy() ? "SEHAT" : "PERLU PERHATIAN"),
                        "Alasan: " + report.reasons().size(),
                        "Repair Staging: tersedia",
                        "Aktivasi + Verifikasi: tersedia",
                        "Rollback Otomatis: tersedia",
                        "Safe Mode: " + kernel.safeModeController().statusIndonesia()
                ),
                value -> {}
        );
    }

    private void showBuild() {
        FullProductVerifier.Result product =
                new FullProductVerifier().verify(kernel);
        boolean buildReady = kernel.readyCoordinator().preview().isPass();
        showCommandOverlay(
                "Build & READY",
                Arrays.asList(
                        "Kelengkapan Produk: "
                                + (product.isPass() ? "LULUS" : "BELUM LULUS"),
                        "Komponen Wajib: " + product.available().size()
                                + "/" + product.requiredCount(),
                        "Validator Build: " + (buildReady ? "LULUS" : "BLOKIR"),
                        "IR Kanonik: tersedia",
                        "Signing: hanya jalur Private",
                        "Firebase: hanya setelah izin pengguna"
                ),
                value -> {}
        );
    }

    private void showDiagnostics() {
        FullProductVerifier.Result result =
                new FullProductVerifier().verify(kernel);
        List<String> rows = new ArrayList<>();
        rows.add("Kelengkapan: " + (result.isPass() ? "LULUS" : "GAGAL"));
        rows.add("Masalah wajib: " + result.errors().size());
        rows.add("Engine aktif: " + kernel.engineManager().snapshot().size());
        rows.add("Komponen siap: "
                + kernel.libraryManager().components().allReady().size());
        rows.add("Aset siap: "
                + kernel.libraryManager().assets().allReady().size());
        rows.add("Template siap: "
                + kernel.libraryManager().templates().allReady().size());
        rows.add("⧉ Salin Laporan");
        showCommandOverlay("Diagnostik", rows, value -> {});
    }

    private void showRecovery() {
        try {
            int candidates = kernel.projectManager().recoveryCandidates().size();
            showCommandOverlay(
                    "Pemulihan & Backup",
                    Arrays.asList(
                            "Kandidat Pemulihan: " + candidates,
                            "Final Recovery Snapshot: didukung",
                            "Last Valid Recovery: didukung",
                            "Riwayat Revisi: didukung",
                            "Backup Pengguna: " + kernel.productServices().backups().records().size(),
                            "Buat Backup Terverifikasi"
                    ),
                    value -> {
                        if ("Buat Backup Terverifikasi".equals(value)) {
                            try {
                                kernel.productServices().backups().createVerified();
                                toast("Backup terverifikasi dibuat.");
                                closeOverlay();
                            } catch (Exception error) {
                                toast("Backup gagal dibuat.");
                            }
                        }
                    }
            );
        } catch (IOException error) {
            toast("Daftar pemulihan tidak dapat dibaca.");
        }
    }

    private void showEvolution() {
        showCommandOverlay(
                "Evolusi Tanpa Rebuild",
                Arrays.asList(
                        "App.patch deklaratif: SIAP",
                        "Verifikasi signature remote: WAJIB",
                        "Staging: SIAP",
                        "Pratinjau perubahan: SIAP",
                        "Recovery point sebelum mutasi: WAJIB",
                        "Apply atomik: SIAP",
                        "Health Check: SIAP",
                        "Rollback: SIAP",
                        "Kode executable baru: memerlukan APK baru"
                ),
                value -> {}
        );
    }

    private void showInstalledTarget() {
        com.toolbox.tools.live.CapabilityScanResult scan =
                kernel.capabilityScanner().scan(kernel.selfTargetDescriptor());
        showCommandOverlay(
                "Capability Scan",
                Arrays.asList(
                        "Target: " + kernel.selfTargetDescriptor().labelIndonesia(),
                        "Terinstal: " + (scan.installed() ? "YA" : "TIDAK"),
                        "UI: " + availability(scan, com.toolbox.tools.live.CapabilityArea.UI),
                        "Logika: " + availability(scan, com.toolbox.tools.live.CapabilityArea.LOGIC),
                        "Data: " + availability(scan, com.toolbox.tools.live.CapabilityArea.DATA),
                        "Pengikatan: " + availability(scan, com.toolbox.tools.live.CapabilityArea.BINDING),
                        "Aset: " + availability(scan, com.toolbox.tools.live.CapabilityArea.ASSET),
                        "Runtime Apply: " + availability(scan, com.toolbox.tools.live.CapabilityArea.RUNTIME)
                ),
                value -> {}
        );
    }

    private void showSelfEdit() {
        showCommandOverlay(
                "Edit ToolBox",
                Arrays.asList(
                        "Surface deklaratif: DAPAT DIEDIT",
                        "UI/Logika/Data/Pengikatan/Aset: capability-based",
                        "Kernel: TERPROTEKSI",
                        "Recovery Core: TERPROTEKSI",
                        "Safety Core: TERPROTEKSI",
                        "Alur: Working → Staging → Validasi → Recovery → Aktivasi → Verifikasi"
                ),
                value -> {}
        );
    }

    private void showFloatingEditor(
            String title,
            List<String> rows
    ) {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(VISIBLE);
        overlayLayer.setOnClickListener(v -> closeOverlay());

        LinearLayout card = UiKit.kolom(getContext());
        card.setPadding(
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 12),
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14)
        );
        card.setBackground(UiKit.kartuPx(
                getContext(),
                Color.rgb(9, 26, 34),
                UiKit.NEON_BIRU,
                20,
                1
        ));
        card.setElevation(UiKit.dp(getContext(), 14));
        card.setOnClickListener(v -> {});

        LinearLayout header = UiKit.baris(getContext());
        TextView t = UiKit.judul(getContext(), title, 15f);
        t.setTextColor(UiKit.NEON_BIRU);
        header.addView(t, new LinearLayout.LayoutParams(
                0, UiKit.dp(getContext(), 38), 1
        ));
        TextView close = UiKit.chip(getContext(), "Tutup", false);
        close.setOnClickListener(v -> closeOverlay());
        header.addView(close);
        card.addView(header);

        for (String row : rows) {
            TextView item = UiKit.tombol(getContext(), row, false);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setTextSize(11.5f);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    UiKit.dp(getContext(), 42)
            );
            p.bottomMargin = UiKit.dp(getContext(), 5);
            card.addView(item, p);
        }

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                UiKit.dp(getContext(), 286),
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlayLayer.addView(card, cp);
    }

    private interface CommandHandler {
        void onCommand(String value);
    }

    private void showCommandOverlay(
            String title,
            List<String> rows,
            CommandHandler handler
    ) {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(VISIBLE);
        overlayLayer.setBackgroundColor(Color.argb(150, 0, 0, 0));
        overlayLayer.setOnClickListener(v -> closeOverlay());

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout card = UiKit.kolom(getContext());
        card.setPadding(
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14)
        );
        card.setBackground(UiKit.kartuPx(
                getContext(),
                Color.rgb(9, 26, 34),
                UiKit.NEON,
                22,
                1
        ));
        card.setOnClickListener(v -> {});
        scroll.addView(card);

        LinearLayout header = UiKit.baris(getContext());
        TextView t = UiKit.judul(getContext(), title, 17f);
        t.setTextColor(UiKit.NEON);
        header.addView(t, new LinearLayout.LayoutParams(
                0, UiKit.dp(getContext(), 42), 1
        ));
        TextView close = UiKit.chip(getContext(), "Tutup", false);
        close.setOnClickListener(v -> closeOverlay());
        header.addView(close);
        card.addView(header);

        for (String row : rows) {
            TextView item = UiKit.tombol(getContext(), row, false);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setTextSize(12f);
            item.setOnClickListener(v -> handler.onCommand(row));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    UiKit.dp(getContext(), 44)
            );
            p.bottomMargin = UiKit.dp(getContext(), 6);
            card.addView(item, p);
        }

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                Math.min(UiKit.dp(getContext(), 340),
                        getResources().getDisplayMetrics().widthPixels
                                - UiKit.dp(getContext(), 32)),
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlayLayer.addView(scroll, sp);
    }

    private void closeOverlay() {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(GONE);
        overlayLayer.setBackgroundColor(Color.TRANSPARENT);
    }

    private List<String> editorOptions(String label) {
        if ("Ukuran".equals(label)) {
            return Arrays.asList(
                    "Lebar: 148 dp",
                    "Tinggi: 46 dp",
                    "Fixed / Content / Fill",
                    "Kunci Rasio",
                    "Snap",
                    "Reset"
            );
        }
        if ("Posisi".equals(label)) {
            return Arrays.asList(
                    "Mode Terikat Layout",
                    "Mode Posisi Bebas",
                    "Anchor",
                    "Grid & Guide",
                    "Snap Edge / Center / Object"
            );
        }
        if ("Warna".equals(label)) {
            return Arrays.asList(
                    "Token",
                    "Palet",
                    "Kustom",
                    "Gradient",
                    "Transparan",
                    "Terbaru",
                    "Favorit"
            );
        }
        if ("Spasi".equals(label)) {
            return Arrays.asList(
                    "Padding",
                    "Margin",
                    "Spacing",
                    "Atas / Kanan / Bawah / Kiri",
                    "Linked / Unlinked"
            );
        }
        if ("Aksesibilitas".equals(label)) {
            return Arrays.asList(
                    "Label: Buka Detail",
                    "Role: Tombol",
                    "Focus: Aktif",
                    "Urutan Fokus",
                    "Status / Error"
            );
        }
        if ("Animasi".equals(label)) {
            return Arrays.asList(
                    "Fade",
                    "Slide",
                    "Scale",
                    "Rotate",
                    "Trigger",
                    "Durasi / Delay / Easing",
                    "Pratinjau Sekali"
            );
        }
        return Arrays.asList(
                "Gunakan nilai bawaan",
                "Ubah nilai",
                "Reset",
                "Kunci properti",
                "Tambahkan ke Favorit"
        );
    }

    private void saveProject() {
        try {
            kernel.projectManager().save();
            toast("Proyek tersimpan secara transaksional.");
            updateStatus();
        } catch (Exception error) {
            toast("Simpan gagal aman. Revisi valid sebelumnya tetap dipertahankan.");
        }
    }

    private void undo() {
        boolean done = kernel.editorEnvironment().visualSession().undo();
        if (!done) done = kernel.projectManager().undo();
        toast(done ? "Perubahan diurungkan." : "Tidak ada perubahan untuk diurungkan.");
        renderWorkspace();
        updateStatus();
    }

    private void redo() {
        boolean done = kernel.editorEnvironment().visualSession().redo();
        if (!done) done = kernel.projectManager().redo();
        toast(done ? "Perubahan diulangi." : "Tidak ada perubahan untuk diulangi.");
        renderWorkspace();
        updateStatus();
    }

    private void updateStatus() {
        FullProductVerifier.Result result =
                new FullProductVerifier().verify(kernel);
        statusLine.setText(
                labelSection(active)
                        + " • " + labelRepresentation()
                        + " • " + labelEditorMode()
                        + " • Produk " + (result.isPass() ? "LULUS" : "BELUM LULUS")
                        + " • " + result.available().size()
                        + "/" + result.requiredCount()
        );
        statusLine.setTextColor(result.isPass() ? UiKit.NEON : UiKit.PERINGATAN);
    }

    private String labelSection(AuthoringSection section) {
        switch (section) {
            case UI: return "UI";
            case LOGIC: return "Logika";
            case DATA: return "Data";
            case BINDING: return "Pengikatan";
            case ASSET: return "Aset";
            default: return "Editor";
        }
    }

    private String labelRepresentation() {
        switch (representation) {
            case PROPERTI: return "Properti";
            case KODE: return "Kode";
            case VISUAL:
            default: return "Visual";
        }
    }

    private String labelEditorMode() {
        switch (kernel.editorEnvironment().shell().mode()) {
            case PREVIEW: return "Pratinjau";
            case TEST: return "Uji";
            case LIVE: return "Langsung";
            case EDIT:
            default: return "Edit";
        }
    }

    private static String freezeStateIndonesia(FreezeEngine.State state) {
        switch (state) {
            case NORMAL: return "Normal";
            case CREATING_SNAPSHOT: return "Membuat Snapshot";
            case FROZEN: return "Beku";
            case COMMITTING: return "Menyimpan Baseline Kerja";
            case RESTORING: return "Memulihkan";
            case THAWING: return "Kembali Normal";
            case VERIFYING: return "Memverifikasi";
            case RECOVERY_REQUIRED: return "Pemulihan Diperlukan";
            case FAILED_SAFE:
            default: return "Gagal Aman";
        }
    }

    private static String availability(
            com.toolbox.tools.live.CapabilityScanResult scan,
            com.toolbox.tools.live.CapabilityArea area
    ) {
        switch (scan.status(area)) {
            case AVAILABLE: return "TERSEDIA";
            case READ_ONLY: return "HANYA BACA";
            case UNAVAILABLE:
            default: return "TIDAK TERSEDIA";
        }
    }

    private static String translate(String value) {
        if (value == null) return "";
        return value
                .replace("Logic", "Logika")
                .replace("Binding", "Pengikatan")
                .replace("Asset", "Aset")
                .replace("Object", "Objek")
                .replace("Recent", "Terbaru")
                .replace("Favorite", "Favorit")
                .replace("Preview", "Pratinjau")
                .replace("Usage", "Penggunaan")
                .replace("Import", "Impor")
                .replace("Dependency", "Dependensi");
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private static View horizontal(LinearLayout child) {
        HorizontalScrollView h = new HorizontalScrollView(child.getContext());
        h.setHorizontalScrollBarEnabled(false);
        h.addView(child);
        return h;
    }

    private void toast(String text) {
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }
}
