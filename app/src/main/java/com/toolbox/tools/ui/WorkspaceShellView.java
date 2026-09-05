package com.toolbox.tools.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Debug;
import android.graphics.drawable.GradientDrawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.editor.EdgeItem;
import com.toolbox.tools.editor.EdgePanelModel;
import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.EditorRect;
import com.toolbox.tools.editor.VisualCapability;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.product.FreezeEngine;
import com.toolbox.tools.product.FullProductVerifier;
import com.toolbox.tools.product.ProductAcceptanceMatrix;
import com.toolbox.tools.product.ProductCompletionServices;
import com.toolbox.tools.repair.HealthReport;
import com.toolbox.tools.protocol.ManagedAppProtocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkspaceShellView extends FrameLayout {
    private enum Screen {
        HOME,
        EDITOR_CHOOSER,
        EDITOR_WORKSPACE
    }

    private enum Representation {
        VISUAL,
        PROPERTI,
        KODE
    }

    private enum PanelPage {
        ROOT,
        FUNCTIONS,
        REPRESENTATION,
        MODES,
        CONTEXT
    }

    private enum EdgeAnchor {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    private final AppKernel kernel;
    private final FrameLayout workspace;
    private final FrameLayout edgeContainer;
    private final LinearLayout edgeContent;
    private final TextView edgeHandle;
    private final FrameLayout overlayLayer;
    private final FrameLayout bubbleQuickLayer;
    private final TextView bubble;

    private Screen screen = Screen.HOME;
    private PanelPage panelPage = PanelPage.ROOT;
    private Representation representation = Representation.VISUAL;
    private AuthoringSection active = AuthoringSection.UI;
    private String editorEntry = "Proyek Tersimpan";
    private boolean edgeOpen = true;
    private EdgeAnchor edgeAnchor;
    private Insets systemInsets = Insets.NONE;
    private boolean edgeDragActive;
    private String activeTargetPackage;
    private String activeTargetSession;
    private long lastSoakPssDriftBytes;

    private boolean bubbleDragging;
    private boolean bubbleQuickWasVisibleOnDown;
    private float bubbleDownX;
    private float bubbleDownY;
    private float bubbleStartX;
    private float bubbleStartY;

    public WorkspaceShellView(Context context, AppKernel kernel) {
        super(context);
        this.kernel = kernel;
        setBackgroundColor(UiKit.LATAR);
        setClipChildren(false);
        setClipToPadding(false);
        setOnApplyWindowInsetsListener((view, insets) -> {
            systemInsets = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
            );
            kernel.productServices().visualLayout().setSafeInsets(
                    new com.toolbox.tools.product.VisualLayoutEngine.Insets(
                            systemInsets.left,
                            systemInsets.top,
                            systemInsets.right,
                            systemInsets.bottom
                    )
            );
            applyEdgeLayout(false);
            post(this::clampBubbleToSafeArea);
            return insets;
        });

        edgeAnchor = restoreEdgeAnchor();

        workspace = new FrameLayout(context);
        workspace.setBackgroundColor(UiKit.LATAR);
        addView(workspace, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        edgeContainer = new FrameLayout(context);
        edgeContainer.setBackground(UiKit.kartuPx(
                context,
                Color.rgb(8, 24, 31),
                UiKit.GARIS,
                18,
                1
        ));
        addView(edgeContainer);

        ScrollView edgeScroll = new ScrollView(context);
        edgeScroll.setFillViewport(true);
        edgeScroll.setVerticalScrollBarEnabled(false);
        edgeContent = UiKit.kolom(context);
        edgeContent.setPadding(
                UiKit.dp(context, 12),
                UiKit.dp(context, 12),
                UiKit.dp(context, 12),
                UiKit.dp(context, 18)
        );
        edgeScroll.addView(edgeContent);
        edgeContainer.addView(edgeScroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        edgeHandle = UiKit.judul(context, "", 22f);
        edgeHandle.setGravity(Gravity.CENTER);
        edgeHandle.setTextColor(UiKit.NEON);
        edgeHandle.setBackground(UiKit.kartuPx(
                context,
                UiKit.PERMUKAAN_2,
                UiKit.NEON,
                14,
                1
        ));
        edgeHandle.setElevation(UiKit.dp(context, 16));
        edgeHandle.setClickable(true);
        edgeHandle.setFocusable(true);
        addView(edgeHandle);

        GestureDetector edgeGesture = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        toggleEdge();
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        repositionEdge();
                    }

                    @Override
                    public boolean onScroll(
                            MotionEvent e1,
                            MotionEvent e2,
                            float distanceX,
                            float distanceY
                    ) {
                        edgeDragActive = true;
                        applyProgressiveEdgeDrag(e1, e2);
                        return true;
                    }

                    @Override
                    public boolean onFling(
                            MotionEvent e1,
                            MotionEvent e2,
                            float velocityX,
                            float velocityY
                    ) {
                        if (isLandscape()) {
                            if (Math.abs(velocityY) > Math.abs(velocityX)) {
                                setEdgeOpen(velocityY < 0
                                        ? edgeAnchor == EdgeAnchor.BOTTOM
                                        : edgeAnchor == EdgeAnchor.TOP);
                                if (edgeAnchor == EdgeAnchor.BOTTOM && velocityY > 0) setEdgeOpen(false);
                                if (edgeAnchor == EdgeAnchor.TOP && velocityY < 0) setEdgeOpen(false);
                                return true;
                            }
                        } else if (Math.abs(velocityX) > Math.abs(velocityY)) {
                            if (edgeAnchor == EdgeAnchor.RIGHT) {
                                setEdgeOpen(velocityX < 0);
                            } else {
                                setEdgeOpen(velocityX > 0);
                            }
                            return true;
                        }
                        return false;
                    }
                }
        );
        edgeHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                edgeDragActive = false;
            }
            boolean handled = edgeGesture.onTouchEvent(event);
            if ((event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                    && edgeDragActive) {
                snapProgressiveEdgeDrag();
                return true;
            }
            return handled;
        });

        overlayLayer = new FrameLayout(context);
        overlayLayer.setVisibility(GONE);
        addView(overlayLayer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        bubbleQuickLayer = new FrameLayout(context);
        bubbleQuickLayer.setVisibility(GONE);
        bubbleQuickLayer.setBackgroundColor(Color.TRANSPARENT);
        bubbleQuickLayer.setClickable(false);
        addView(bubbleQuickLayer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        bubble = UiKit.judul(context, "TB", 14f);
        bubble.setContentDescription("Bubble ToolBox");
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(UiKit.LATAR);
        bubble.setBackground(circle(UiKit.NEON));
        bubble.setElevation(UiKit.dp(context, 20));
        bubble.setOnTouchListener(this::handleBubbleTouch);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                UiKit.dp(context, 56),
                UiKit.dp(context, 56),
                Gravity.TOP | Gravity.START
        );
        bubbleParams.leftMargin = UiKit.dp(context, 16);
        bubbleParams.topMargin = UiKit.dp(context, 100);
        addView(bubble, bubbleParams);

        kernel.editorEnvironment().shell().setLiveCapability(true);
        applyEdgeLayout(false);
        renderAll();
        post(() -> {
            restoreBubblePosition();
            applyEdgeLayout(false);
            sampleRuntimeResources();
        });
    }

    public boolean handleBack() {
        if (bubbleQuickLayer.getVisibility() == VISIBLE) {
            hideBubbleShortcuts();
            return true;
        }
        if (overlayLayer.getVisibility() == VISIBLE) {
            closeOverlay();
            return true;
        }
        if (panelPage != PanelPage.ROOT) {
            panelPage = PanelPage.ROOT;
            renderEdge();
            return true;
        }
        if (screen == Screen.EDITOR_WORKSPACE) {
            openEditorChooser();
            return true;
        }
        if (screen == Screen.EDITOR_CHOOSER) {
            openHome();
            return true;
        }
        return false;
    }

    private void renderAll() {
        renderWorkspace();
        renderEdge();
        post(this::sampleRuntimeResources);
    }

    private void renderWorkspace() {
        workspace.removeAllViews();
        switch (screen) {
            case HOME:
                renderHome();
                break;
            case EDITOR_CHOOSER:
                renderEditorChooser();
                break;
            case EDITOR_WORKSPACE:
                renderEditorWorkspace();
                break;
            default:
                throw new IllegalStateException("layar tidak dikenal");
        }
    }

    private void renderHome() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout root = UiKit.kolom(getContext());
        root.setPadding(
                UiKit.dp(getContext(), 24),
                UiKit.dp(getContext(), 28),
                UiKit.dp(getContext(), 24),
                UiKit.dp(getContext(), 28)
        );
        scroll.addView(root);

        TextView title = UiKit.judul(getContext(), "ToolBox", 30f);
        title.setTextColor(UiKit.NEON);
        root.addView(title);

        TextView subtitle = UiKit.teks(
                getContext(),
                "Beranda ToolBox • ruang kerja aplikasi",
                13f,
                UiKit.TEKS_REDUP
        );
        subtitle.setPadding(0, UiKit.dp(getContext(), 4), 0, UiKit.dp(getContext(), 20));
        root.addView(subtitle);

        LinearLayout project = card();
        project.addView(UiKit.labelBagian(getContext(), "PROYEK AKTIF"));
        project.addView(UiKit.judul(
                getContext(),
                kernel.projectManager().current().projectId(),
                18f
        ));
        addInfo(
                project,
                "Status kerja",
                kernel.projectManager().hasUnsavedChanges()
                        ? "Ada perubahan yang belum disimpan"
                        : "Semua perubahan tersimpan"
        );
        addInfo(
                project,
                "Revisi",
                String.valueOf(kernel.projectManager().savedRevision())
        );
        root.addView(project);

        UiKit.ruang(root, getContext(), 14);

        LinearLayout activity = card();
        activity.addView(UiKit.labelBagian(getContext(), "AKTIVITAS TOOLBOX"));
        addInfo(activity, "Ruang kerja visual", "Masuk ke Editor melalui Edge Panel.");
        addInfo(activity, "Pemulihan", "Backup, recovery, dan Safe Mode tersedia dari panel.");
        addInfo(activity, "Evolusi", "Paket perubahan dikelola melalui jalur staging dan verifikasi.");
        root.addView(activity);

        UiKit.ruang(root, getContext(), 14);

        FullProductVerifier.Result result = new FullProductVerifier().verify(kernel);
        LinearLayout health = card();
        health.addView(UiKit.labelBagian(getContext(), "KESEHATAN SISTEM"));
        addInfo(
                health,
                "Core",
                result.isPass()
                        ? "Siap digunakan"
                        : "Perlu pemeriksaan • " + result.errors().size() + " masalah"
        );
        addInfo(
                health,
                "Mode Simpan",
                kernel.productServices().freeze().state().name()
        );
        addInfo(
                health,
                "Shell",
                "Bubble + satu Edge Panel kontekstual"
        );
        root.addView(health);

        workspace.addView(scroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    private void renderEditorChooser() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout root = UiKit.kolom(getContext());
        root.setPadding(
                UiKit.dp(getContext(), 24),
                UiKit.dp(getContext(), 28),
                UiKit.dp(getContext(), 270),
                UiKit.dp(getContext(), 28)
        );
        scroll.addView(root);

        TextView title = UiKit.judul(getContext(), "Editor", 27f);
        title.setTextColor(UiKit.NEON);
        root.addView(title);

        TextView desc = UiKit.teks(
                getContext(),
                "Pilih Jalur Editor dari panel. Keempat jalur masuk ke Editor terpadu yang sama; "
                        + "konteks dan kapabilitasnya yang berbeda.",
                12.5f,
                UiKit.TEKS_REDUP
        );
        desc.setPadding(0, UiKit.dp(getContext(), 5), 0, UiKit.dp(getContext(), 18));
        root.addView(desc);

        LinearLayout card = card();
        addInfo(card, "Pilihan 1 • Proyek Tersimpan", "Project Store • revisi " + kernel.projectManager().savedRevision());
        addInfo(
                card,
                "Pilihan 2 • Aplikasi Terinstal",
                "ToolBox-aware ditemukan: "
                        + kernel.productServices().completion().installedTargets.all().size()
                        + " • tanpa bypass sandbox/signature."
        );
        addInfo(card, "Pilihan 3 • Edit ToolBox", "Permukaan deklaratif dapat diedit; kernel/recovery/safety core terlindungi.");
        addInfo(card, "Pilihan 4 • Buat / Edit Komponen", "Component Registry • varian • composite • template.");
        root.addView(card);

        workspace.addView(scroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    private void renderEditorWorkspace() {
        View pane;
        if (kernel.editorEnvironment().shell().mode() != EditorMode.EDIT) {
            pane = new UiCanvasView(getContext(), kernel, objectId -> {
                panelPage = PanelPage.CONTEXT;
                renderEdge();
            });
        } else {
            switch (representation) {
                case PROPERTI:
                    pane = EditorPaneFactory.properties(getContext(), kernel, active);
                    break;
                case KODE:
                    pane = EditorPaneFactory.code(getContext(), kernel, active);
                    break;
                case VISUAL:
                default:
                    pane = EditorPaneFactory.visual(
                            getContext(),
                            kernel,
                            active,
                            objectId -> {
                                panelPage = PanelPage.CONTEXT;
                                renderEdge();
                            }
                    );
                    break;
            }
        }

        FrameLayout holder = new FrameLayout(getContext());
        holder.setBackgroundColor(UiKit.LATAR);
        FrameLayout.LayoutParams paneParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        paneParams.rightMargin = UiKit.dp(getContext(), 8);
        holder.addView(pane, paneParams);

        TextView context = UiKit.teks(
                getContext(),
                "Editor • " + editorEntry + " • " + labelSection(active)
                        + " • " + labelRepresentation() + " • " + labelEditorMode(),
                10.5f,
                UiKit.TEKS_REDUP
        );
        context.setPadding(
                UiKit.dp(getContext(), 12),
                UiKit.dp(getContext(), 5),
                UiKit.dp(getContext(), 12),
                UiKit.dp(getContext(), 5)
        );
        context.setBackgroundColor(Color.argb(210, 7, 16, 22));
        holder.addView(context, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(getContext(), 30),
                Gravity.TOP
        ));

        workspace.addView(holder, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    private void renderEdge() {
        edgeContent.removeAllViews();

        if (screen == Screen.HOME) {
            edgeHeader("ToolBox", "Panel Utama");
            addEdgeCommand("Editor", this::openEditorChooser);
            addEdgeCommand("Proyek & File", () -> showProjectInfo());
            addEdgeCommand("Pemulihan & Backup", this::showRecovery);
            addEdgeCommand("Freeze / Mode Simpan", this::showFreeze);
            addEdgeCommand("Perbaikan & Kesehatan", this::showHealth);
            addEdgeCommand("Paket Evolusi Tanpa Rebuild", this::showEvolution);
            addEdgeCommand("Bangun & SIAP", this::showBuild);
            addEdgeCommand("Diagnostik", this::showDiagnostics);
            addEdgeCommand("Pengaturan", this::showSettings);
            return;
        }

        if (screen == Screen.EDITOR_CHOOSER) {
            edgeHeader("Editor", "4 Pilihan Edit");
            addEdgeCommand("Proyek Tersimpan", () -> openEditor("Proyek Tersimpan"));
            addEdgeCommand("Aplikasi Terinstal", this::showInstalledTargets);
            addEdgeCommand("Edit ToolBox", () -> openEditor("Edit ToolBox"));
            addEdgeCommand("Buat / Edit Komponen", () -> openEditor("Buat / Edit Komponen"));
            addEdgeCommand("‹ Antarmuka ToolBox", this::openHome);
            return;
        }

        switch (panelPage) {
            case FUNCTIONS:
                edgeHeader("Editor", "Fungsi 5-in-1");
                addFunction("UI", AuthoringSection.UI);
                addFunction("Logika", AuthoringSection.LOGIC);
                addFunction("Data", AuthoringSection.DATA);
                addFunction("Pengikatan", AuthoringSection.BINDING);
                addFunction("Aset", AuthoringSection.ASSET);
                addEdgeCommand("‹ Kembali", this::edgeRoot);
                break;
            case REPRESENTATION:
                edgeHeader("Editor", "Representasi");
                addEdgeCommand(mark("Visual", representation == Representation.VISUAL),
                        () -> setRepresentation(Representation.VISUAL));
                addEdgeCommand(mark("Properti", representation == Representation.PROPERTI),
                        () -> setRepresentation(Representation.PROPERTI));
                addEdgeCommand(mark("Kode", representation == Representation.KODE),
                        () -> setRepresentation(Representation.KODE));
                addEdgeCommand("‹ Kembali", this::edgeRoot);
                break;
            case MODES:
                edgeHeader("Editor", "Mode Kerja");
                EditorMode mode = kernel.editorEnvironment().shell().mode();
                addEdgeCommand(mark("Edit", mode == EditorMode.EDIT), () -> setRuntimeMode(EditorMode.EDIT));
                addEdgeCommand(mark("Pratinjau", mode == EditorMode.PREVIEW), () -> setRuntimeMode(EditorMode.PREVIEW));
                addEdgeCommand(mark("Uji", mode == EditorMode.TEST), () -> setRuntimeMode(EditorMode.TEST));
                addEdgeCommand(mark("Langsung", mode == EditorMode.LIVE), () -> setRuntimeMode(EditorMode.LIVE));
                addEdgeCommand("‹ Kembali", this::edgeRoot);
                break;
            case CONTEXT:
                renderContextEdge();
                break;
            case ROOT:
            default:
                renderEditorRootEdge();
                break;
        }
    }

    private void renderEditorRootEdge() {
        edgeHeader("Editor", editorEntry);
        addEdgeCommand("Fungsi Editor • 5-in-1", () -> {
            panelPage = PanelPage.FUNCTIONS;
            renderEdge();
        });
        addEdgeCommand("Representasi • " + labelRepresentation(), () -> {
            panelPage = PanelPage.REPRESENTATION;
            renderEdge();
        });
        addEdgeCommand("Mode Kerja • " + labelEditorMode(), () -> {
            panelPage = PanelPage.MODES;
            renderEdge();
        });
        addEdgeCommand(
                kernel.editorEnvironment().shell().editEnabled()
                        ? "Edit Objek • AKTIF"
                        : "Edit Objek • NONAKTIF",
                this::toggleEditEnabled
        );
        addEdgeCommand("Konteks • " + labelSection(active), () -> {
            panelPage = PanelPage.CONTEXT;
            renderEdge();
        });
        addEdgeHeading("Perubahan");
        addEdgeCommand("Simpan", this::saveProject);
        addEdgeCommand("Urungkan", this::undo);
        addEdgeCommand("Ulangi", this::redo);
        addEdgeCommand("‹ Pilih Jalur Editor", this::openEditorChooser);
        addEdgeCommand("⌂ Antarmuka ToolBox", this::openHome);
    }

    private void renderContextEdge() {
        EdgePanelModel model = kernel.editorEnvironment().shell().edgePanel(
                VisualCapabilitySet.defaultEditable()
        );
        edgeHeader(translate(model.titleIndonesia()), translate(model.breadcrumb()));
        for (EdgeItem item : model.items()) {
            String actionLabel = translate(item.labelIndonesia());
            String displayLabel = contextDisplayLabel(active, actionLabel);
            TextView command = addEdgeCommand(
                    displayLabel,
                    () -> showEditorAction(actionLabel)
            );
            if (active == AuthoringSection.UI
                    && ("Komponen".equals(actionLabel)
                    || "Aset".equals(actionLabel))) {
                command.setContentDescription(
                        displayLabel + " • ketuk untuk pilih • tekan lama lalu seret ke canvas"
                );
                command.setOnLongClickListener(v -> {
                    String payload = "Komponen".equals(actionLabel)
                            ? "component.button"
                            : "asset.theme.dark.neon";
                    ClipData data = ClipData.newPlainText(
                            "toolbox-drop",
                            payload
                    );
                    v.startDragAndDrop(
                            data,
                            new View.DragShadowBuilder(v),
                            payload,
                            0
                    );
                    return true;
                });
            }
        }
        addEdgeCommand("‹ Kembali", this::edgeRoot);
    }

    private void addFunction(String label, AuthoringSection section) {
        addEdgeCommand(mark(label, active == section), () -> {
            active = section;
            activateToolSection(section);
            panelPage = PanelPage.ROOT;
            renderWorkspace();
            renderEdge();
        });
    }

    private void edgeRoot() {
        panelPage = PanelPage.ROOT;
        renderEdge();
    }

    private void edgeHeader(String title, String crumb) {
        TextView c = UiKit.teks(getContext(), crumb, 10f, UiKit.TEKS_REDUP);
        edgeContent.addView(c);
        TextView t = UiKit.judul(getContext(), title, 16f);
        t.setTextColor(UiKit.NEON_BIRU);
        t.setPadding(0, UiKit.dp(getContext(), 3), 0, UiKit.dp(getContext(), 10));
        edgeContent.addView(t);
    }

    private void addEdgeHeading(String label) {
        TextView v = UiKit.labelBagian(getContext(), label.toUpperCase());
        v.setPadding(0, UiKit.dp(getContext(), 9), 0, UiKit.dp(getContext(), 4));
        edgeContent.addView(v);
    }

    private TextView addEdgeCommand(String label, Runnable action) {
        TextView v = UiKit.tombol(getContext(), label, false);
        v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        v.setTextSize(12f);
        v.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(getContext(), 44)
        );
        p.bottomMargin = UiKit.dp(getContext(), 6);
        edgeContent.addView(v, p);
        return v;
    }

    private void openHome() {
        closeOverlay();
        screen = Screen.HOME;
        panelPage = PanelPage.ROOT;
        kernel.editorEnvironment().shell().clearSelection();
        renderAll();
    }

    private void openEditorChooser() {
        closeOverlay();
        screen = Screen.EDITOR_CHOOSER;
        panelPage = PanelPage.ROOT;
        kernel.editorEnvironment().shell().clearSelection();
        renderAll();
    }

    private void openEditor(String entry) {
        kernel.productServices().completion().uiStateHold.enterEdit(
                stateHoldId()
        );
        editorEntry = entry;
        screen = Screen.EDITOR_WORKSPACE;
        panelPage = PanelPage.ROOT;
        representation = Representation.VISUAL;
        active = AuthoringSection.UI;
        activateToolSection(active);
        kernel.editorEnvironment().shell().setMode(EditorMode.EDIT);
        kernel.editorEnvironment().shell().setEditEnabled(true);
        renderAll();
    }

    private void setRepresentation(Representation next) {
        representation = next;
        panelPage = PanelPage.ROOT;
        renderAll();
    }

    private void setRuntimeMode(EditorMode mode) {
        try {
            EditorMode before = kernel.editorEnvironment().shell().mode();
            if (before == EditorMode.EDIT && mode != EditorMode.EDIT) {
                kernel.productServices().completion().uiStateHold.exitEdit(
                        stateHoldId()
                );
            } else if (before != EditorMode.EDIT && mode == EditorMode.EDIT) {
                kernel.productServices().completion().uiStateHold.enterEdit(
                        stateHoldId()
                );
            }
            kernel.editorEnvironment().shell().setMode(mode);
            panelPage = PanelPage.ROOT;
            renderAll();
        } catch (RuntimeException error) {
            toast("Mode Langsung belum tersedia untuk target ini.");
        }
    }

    private void toggleEditEnabled() {
        kernel.editorEnvironment().shell().setEditEnabled(
                !kernel.editorEnvironment().shell().editEnabled()
        );
        renderAll();
    }

    private void toggleEdge() {
        setEdgeOpen(!edgeOpen);
    }

    private void setEdgeOpen(boolean open) {
        edgeOpen = open;
        applyEdgeLayout(false);
    }

    private void repositionEdge() {
        if (isLandscape()) {
            edgeAnchor = edgeAnchor == EdgeAnchor.TOP
                    ? EdgeAnchor.BOTTOM
                    : EdgeAnchor.TOP;
        } else {
            edgeAnchor = edgeAnchor == EdgeAnchor.LEFT
                    ? EdgeAnchor.RIGHT
                    : EdgeAnchor.LEFT;
        }
        persistEdgeAnchor();
        applyEdgeLayout(false);
        toast("Panel dipindahkan ke " + edgeAnchorLabel().toLowerCase(java.util.Locale.ROOT) + ".");
    }

    private void applyEdgeLayout(boolean animate) {
        boolean landscape = isLandscape();
        int panelThickness = UiKit.dp(getContext(), landscape ? 210 : 238);
        int handleLong = UiKit.dp(getContext(), 72);
        int handleShort = UiKit.dp(getContext(), 56);
        int margin = UiKit.dp(getContext(), 4);

        if (landscape && edgeAnchor != EdgeAnchor.TOP && edgeAnchor != EdgeAnchor.BOTTOM) {
            edgeAnchor = EdgeAnchor.BOTTOM;
        } else if (!landscape && edgeAnchor != EdgeAnchor.LEFT && edgeAnchor != EdgeAnchor.RIGHT) {
            edgeAnchor = EdgeAnchor.RIGHT;
        }

        FrameLayout.LayoutParams panelParams;
        FrameLayout.LayoutParams handleParams;
        float tx = 0f;
        float ty = 0f;

        if (!landscape) {
            boolean right = edgeAnchor == EdgeAnchor.RIGHT;
            panelParams = new FrameLayout.LayoutParams(
                    panelThickness,
                    LayoutParams.MATCH_PARENT,
                    (right ? Gravity.END : Gravity.START)
            );
            panelParams.topMargin = UiKit.dp(getContext(), 10);
            panelParams.bottomMargin = UiKit.dp(getContext(), 10);
            if (right) panelParams.rightMargin = margin;
            else panelParams.leftMargin = margin;

            handleParams = new FrameLayout.LayoutParams(
                    handleShort,
                    handleLong,
                    Gravity.CENTER_VERTICAL | (right ? Gravity.END : Gravity.START)
            );
            int openOffset = Math.max(0, panelThickness - handleShort / 2);
            if (right) {
                handleParams.rightMargin = edgeOpen ? openOffset : 0;
                tx = edgeOpen ? 0f : panelThickness - UiKit.dp(getContext(), 8);
            } else {
                handleParams.leftMargin = edgeOpen ? openOffset : 0;
                tx = edgeOpen ? 0f : -(panelThickness - UiKit.dp(getContext(), 8));
            }
        } else {
            boolean bottom = edgeAnchor == EdgeAnchor.BOTTOM;
            panelParams = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    panelThickness,
                    (bottom ? Gravity.BOTTOM : Gravity.TOP)
            );
            panelParams.leftMargin = UiKit.dp(getContext(), 10);
            panelParams.rightMargin = UiKit.dp(getContext(), 10);
            if (bottom) panelParams.bottomMargin = margin;
            else panelParams.topMargin = margin;

            handleParams = new FrameLayout.LayoutParams(
                    handleLong,
                    handleShort,
                    Gravity.CENTER_HORIZONTAL | (bottom ? Gravity.BOTTOM : Gravity.TOP)
            );
            int openOffset = Math.max(0, panelThickness - handleShort / 2);
            if (bottom) {
                handleParams.bottomMargin = edgeOpen ? openOffset : 0;
                ty = edgeOpen ? 0f : panelThickness - UiKit.dp(getContext(), 8);
            } else {
                handleParams.topMargin = edgeOpen ? openOffset : 0;
                ty = edgeOpen ? 0f : -(panelThickness - UiKit.dp(getContext(), 8));
            }
        }

        edgeContainer.setLayoutParams(panelParams);
        edgeHandle.setLayoutParams(handleParams);
        edgeContainer.animate().cancel();
        if (animate) {
            edgeContainer.animate().translationX(tx).translationY(ty).setDuration(120).start();
        } else {
            edgeContainer.setTranslationX(tx);
            edgeContainer.setTranslationY(ty);
        }
        updateEdgeHandleVisual();
        edgeContainer.bringToFront();
        edgeHandle.bringToFront();
        overlayLayer.bringToFront();
        bubbleQuickLayer.bringToFront();
        bubble.bringToFront();
    }

    private void updateEdgeHandleVisual() {
        String symbol;
        if (edgeAnchor == EdgeAnchor.RIGHT) {
            symbol = edgeOpen ? "›" : "‹";
        } else if (edgeAnchor == EdgeAnchor.LEFT) {
            symbol = edgeOpen ? "‹" : "›";
        } else if (edgeAnchor == EdgeAnchor.BOTTOM) {
            symbol = edgeOpen ? "▼" : "▲";
        } else {
            symbol = edgeOpen ? "▲" : "▼";
        }
        edgeHandle.setText(symbol);
        edgeHandle.setContentDescription(
                "Handle panel " + edgeAnchorLabel().toLowerCase(java.util.Locale.ROOT)
                        + " • " + (edgeOpen ? "terbuka" : "tertutup")
                        + " • ketuk buka tutup • tekan lama pindah"
        );
    }

    private boolean handleBubbleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                bubbleQuickWasVisibleOnDown = bubbleQuickLayer.getVisibility() == VISIBLE;
                hideBubbleShortcuts();
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
                if (bubbleDragging) {
                    persistBubblePosition(view);
                } else if (!bubbleQuickWasVisibleOnDown) {
                    showBubbleShortcuts();
                }
                return true;
            default:
                return false;
        }
    }

    private void showBubbleShortcuts() {
        bubbleQuickLayer.removeAllViews();
        bubbleQuickLayer.setVisibility(VISIBLE);
        bubbleQuickLayer.bringToFront();
        bubble.bringToFront();

        addBubbleShortcut(
                kernel.editorEnvironment().shell().editEnabled()
                        ? "Edit AKTIF"
                        : "Edit NONAKTIF",
                0,
                () -> {
                    kernel.editorEnvironment().shell().setEditEnabled(
                            !kernel.editorEnvironment().shell().editEnabled()
                    );
                    hideBubbleShortcuts();
                    renderAll();
                }
        );
        addBubbleShortcut("Tool", 1, () -> {
            hideBubbleShortcuts();
            showTools();
        });
        addBubbleShortcut("Pengaturan", 2, () -> {
            hideBubbleShortcuts();
            showSettings();
        });
        addBubbleShortcut("Floating Window", 3, () -> {
            hideBubbleShortcuts();
            showFloatingContextWindow();
        });
    }

    private void addBubbleShortcut(
            String label,
            int index,
            Runnable action
    ) {
        TextView item = UiKit.chip(getContext(), label, false);
        item.setGravity(Gravity.CENTER);
        item.setTextSize(10.5f);
        item.setElevation(UiKit.dp(getContext(), 18));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription("Pintasan " + label);
        item.setOnClickListener(v -> action.run());

        int width = UiKit.dp(getContext(), 112);
        int height = UiKit.dp(getContext(), 42);
        int gap = UiKit.dp(getContext(), 8);
        int sideGap = UiKit.dp(getContext(), 14);
        int margin = UiKit.dp(getContext(), 8);
        int totalHeight = height * 4 + gap * 3;

        float centerX = bubble.getX() + bubble.getWidth() / 2f;
        float centerY = bubble.getY() + bubble.getHeight() / 2f;
        boolean placeRight = centerX <= getWidth() / 2f;

        float x = placeRight
                ? bubble.getX() + bubble.getWidth() + sideGap
                : bubble.getX() - width - sideGap;
        float maxX = Math.max(margin, getWidth() - width - margin);
        x = Math.max(margin, Math.min(maxX, x));

        float groupTop = centerY - totalHeight / 2f;
        float maxTop = Math.max(margin, getHeight() - totalHeight - margin);
        groupTop = Math.max(margin, Math.min(maxTop, groupTop));
        float y = groupTop + index * (height + gap);

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(width, height);
        p.leftMargin = Math.round(x);
        p.topMargin = Math.round(y);
        bubbleQuickLayer.addView(item, p);
    }

    private void hideBubbleShortcuts() {
        bubbleQuickLayer.removeAllViews();
        bubbleQuickLayer.setVisibility(GONE);
    }

    private void showFloatingContextWindow() {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(VISIBLE);
        overlayLayer.bringToFront();
        bubble.bringToFront();
        overlayLayer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout card = UiKit.kolom(getContext());
        card.setPadding(
                UiKit.dp(getContext(), 12),
                UiKit.dp(getContext(), 12),
                UiKit.dp(getContext(), 12),
                UiKit.dp(getContext(), 12)
        );
        card.setBackground(UiKit.kartuPx(
                getContext(),
                Color.rgb(9, 26, 34),
                UiKit.NEON_BIRU,
                18,
                1
        ));

        LinearLayout header = UiKit.baris(getContext());
        TextView title = UiKit.judul(
                getContext(),
                "Floating Editor • " + labelSection(active),
                14f
        );
        title.setTextColor(UiKit.NEON_BIRU);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                UiKit.dp(getContext(), 40),
                1
        ));
        TextView pin = UiKit.chip(getContext(), "Pin", false);
        TextView resize = UiKit.chip(getContext(), "Ubah Ukuran", false);
        TextView close = UiKit.chip(getContext(), "Tutup", false);
        header.addView(pin);
        header.addView(resize);
        header.addView(close);
        card.addView(header);

        String selected = kernel.editorEnvironment().shell().selectedObjectId();
        if (selected == null) selected = "object.home.primary";
        card.addView(UiKit.teks(
                getContext(),
                "Objek: " + selected
                        + "\nRepresentasi: " + labelRepresentation()
                        + "\nMode: " + labelEditorMode()
                        + "\nSeret header untuk memindahkan.",
                11.5f,
                UiKit.TEKS
        ));

        int safeWidth = Math.max(
                UiKit.dp(getContext(), 220),
                getWidth() - systemInsets.left - systemInsets.right
        );
        int safeHeight = Math.max(
                UiKit.dp(getContext(), 180),
                getHeight() - systemInsets.top - systemInsets.bottom
        );
        int width = Math.min(UiKit.dp(getContext(), 330), safeWidth);
        int height = UiKit.dp(getContext(), 220);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width,
                height,
                Gravity.CENTER
        );
        overlayLayer.addView(card, params);

        try {
            kernel.editorEnvironment().floatingEditor().open(
                    "floating.context",
                    selected,
                    new EditorRect(
                            systemInsets.left,
                            systemInsets.top,
                            Math.max(systemInsets.left + 1, getWidth() - systemInsets.right),
                            Math.max(systemInsets.top + 1, getHeight() - systemInsets.bottom)
                    ),
                    new EditorRect(
                            UiKit.dp(getContext(), 20),
                            UiKit.dp(getContext(), 120),
                            UiKit.dp(getContext(), 180),
                            UiKit.dp(getContext(), 190)
                    ),
                    width,
                    height
            );
        } catch (RuntimeException ignored) {
            // Placement visual tetap tersedia bila controller belum punya ukuran host.
        }

        makeHeaderDraggable(title, card);
        pin.setOnClickListener(v -> {
            try {
                boolean next = kernel.editorEnvironment()
                        .floatingEditor()
                        .active() == null
                        || !kernel.editorEnvironment()
                        .floatingEditor()
                        .active()
                        .pinned();
                kernel.editorEnvironment().floatingEditor().pin(next);
                pin.setText(next ? "Pinned" : "Pin");
            } catch (RuntimeException ignored) {}
        });
        resize.setOnClickListener(v -> {
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            int compact = UiKit.dp(getContext(), 220);
            lp.width = lp.width > compact
                    ? compact
                    : Math.min(UiKit.dp(getContext(), 360), safeWidth);
            lp.height = lp.height > UiKit.dp(getContext(), 180)
                    ? UiKit.dp(getContext(), 180)
                    : Math.min(UiKit.dp(getContext(), 300), safeHeight);
            card.setLayoutParams(lp);
        });
        close.setOnClickListener(v -> {
            kernel.editorEnvironment().floatingEditor().close();
            closeOverlay();
        });
        overlayLayer.setOnClickListener(v -> {
            if (kernel.editorEnvironment().floatingEditor().active() == null
                    || !kernel.editorEnvironment().floatingEditor().active().pinned()) {
                kernel.editorEnvironment().floatingEditor().close();
                closeOverlay();
            }
        });
        card.setOnClickListener(v -> {});
    }

    private void showTools() {
        showActionOverlay(
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
                        "Bangun & SIAP",
                        "Diagnostik"
                ),
                value -> {
                    closeOverlay();
                    if ("Aplikasi Terinstal".equals(value)) {
                        showInstalledTargets();
                    } else if ("Proyek Tersimpan".equals(value)
                            || "Edit ToolBox".equals(value)
                            || "Buat / Edit Komponen".equals(value)) {
                        openEditor(value);
                    } else if ("Pemulihan & Backup".equals(value)) {
                        showRecovery();
                    } else if ("Freeze / Mode Simpan".equals(value)) {
                        showFreeze();
                    } else if ("Perbaikan & Kesehatan".equals(value)) {
                        showHealth();
                    } else if ("Paket Evolusi Tanpa Rebuild".equals(value)) {
                        showEvolution();
                    } else if ("Bangun & SIAP".equals(value)) {
                        showBuild();
                    } else if ("Diagnostik".equals(value)) {
                        showDiagnostics();
                    }
                }
        );
    }

    private void showEditorAction(String label) {
        switch (active) {
            case UI:
                showUiEditorAction(label);
                return;
            case LOGIC:
                showLogicEditorAction(label);
                return;
            case DATA:
                showDataEditorAction(label);
                return;
            case BINDING:
                showBindingEditorAction(label);
                return;
            case ASSET:
                showAssetEditorAction(label);
                return;
            default:
                throw new IllegalStateException("fungsi editor tidak dikenal");
        }
    }

    private void showUiEditorAction(String label) {
        if ("Komponen".equals(label)) {
            showActionOverlay(
                    "Komponen",
                    Arrays.asList("Tombol Aksi", "Tombol Sekunder", "Reset Komponen"),
                    value -> {
                        if ("Tombol Aksi".equals(value)) {
                            applyResource("ui.object.home.primary.text", "Tombol Aksi", "Komponen diterapkan.");
                        } else if ("Tombol Sekunder".equals(value)) {
                            applyResource("ui.object.home.primary.text", "Tombol Sekunder", "Komponen diterapkan.");
                        } else {
                            resetPrimaryObject();
                        }
                    }
            );
            return;
        }
        if ("Template".equals(label)) {
            showActionOverlay(
                    "Template",
                    Arrays.asList("Layar Dasar", "Layar Formulir", "Reset Template"),
                    this::applyTemplate
            );
            return;
        }
        if ("Kit".equals(label) || "Aset".equals(label)
                || "Terbaru".equals(label) || "Favorit".equals(label)) {
            showActionOverlay(
                    label,
                    Arrays.asList("Gelap Neon", "Biru Neon", "Permukaan Tenang"),
                    this::applyStylePreset
            );
            return;
        }

        List<String> rows = uiEditorCommands(label);
        showActionOverlay(label, rows, value -> applyUiEditorCommand(label, value));
    }

    private void showLogicEditorAction(String label) {
        if ("Peristiwa".equals(label)) {
            showActionOverlay(
                    "Peristiwa",
                    Arrays.asList("Saat Ditekan", "Saat Nilai Berubah", "Saat Layar Dibuka"),
                    value -> applyResource(
                            "logic.editor.event",
                            "Saat Ditekan".equals(value) ? "event.click"
                                    : "Saat Nilai Berubah".equals(value) ? "event.change"
                                    : "event.screen.open",
                            "Peristiwa aktif diperbarui."
                    )
            );
            return;
        }
        if ("Aksi".equals(label)) {
            String current = kernel.projectManager().current().resources().getOrDefault(
                    "logic.ui.home.primary.action",
                    "open.detail"
            );
            showActionOverlay(
                    "Aksi",
                    Arrays.asList(
                            mark("Buka Detail", "open.detail".equals(current)),
                            mark("Tampilkan Pesan", "show.message".equals(current)),
                            mark("Simpan", "save.project".equals(current))
                    ),
                    value -> {
                        String clean = stripMark(value);
                        applyResource(
                                "logic.ui.home.primary.action",
                                "Buka Detail".equals(clean) ? "open.detail"
                                        : "Tampilkan Pesan".equals(clean) ? "show.message"
                                        : "save.project",
                                "Aksi alur diperbarui."
                        );
                    }
            );
            return;
        }
        if ("Kondisi".equals(label)) {
            showActionOverlay(
                    "Kondisi",
                    Arrays.asList("Selalu", "Jika Data Valid", "Jika Ada Input"),
                    value -> applyResource(
                            "logic.editor.condition",
                            "Selalu".equals(value) ? "always"
                                    : "Jika Data Valid".equals(value) ? "data.valid"
                                    : "input.present",
                            "Kondisi alur diperbarui."
                    )
            );
            return;
        }
        if ("Alur".equals(label)) {
            showActionOverlay(
                    "Alur",
                    Arrays.asList("Aktifkan Alur Utama", "Nonaktifkan Alur"),
                    value -> applyResource(
                            "flow.home.enabled",
                            "Aktifkan Alur Utama".equals(value) ? "true" : "false",
                            "Status alur diperbarui."
                    )
            );
            return;
        }
        if ("Variabel".equals(label)) {
            showActionOverlay(
                    "Variabel",
                    Arrays.asList("Teks", "Angka", "Boolean"),
                    value -> applyResource(
                            "logic.editor.variable.type",
                            "Teks".equals(value) ? "text"
                                    : "Angka".equals(value) ? "number"
                                    : "boolean",
                            "Tipe variabel diperbarui."
                    )
            );
            return;
        }
        if ("Fungsi".equals(label)) {
            showActionOverlay(
                    "Fungsi",
                    Arrays.asList("Validasi", "Format Teks", "Navigasi"),
                    value -> applyResource(
                            "logic.editor.function",
                            "Validasi".equals(value) ? "validate"
                                    : "Format Teks".equals(value) ? "format.text"
                                    : "navigate",
                            "Fungsi alur diperbarui."
                    )
            );
            return;
        }
        showInfoOverlay("Logika", Arrays.asList("Tidak ada aksi untuk konteks ini."));
    }

    private void showDataEditorAction(String label) {
        if ("Sumber".equals(label)) {
            showActionOverlay(
                    "Sumber Data",
                    Arrays.asList("Lokal", "Data Contoh", "Eksternal Terkelola"),
                    value -> applyResource(
                            "data.editor.source",
                            "Lokal".equals(value) ? "local"
                                    : "Data Contoh".equals(value) ? "mock"
                                    : "managed.external",
                            "Sumber data diperbarui."
                    )
            );
            return;
        }
        if ("Koleksi".equals(label)) {
            showActionOverlay(
                    "Koleksi",
                    Arrays.asList("items", "users", "records"),
                    value -> applyResource(
                            "data.editor.collection",
                            value.toLowerCase(java.util.Locale.ROOT),
                            "Koleksi aktif diperbarui."
                    )
            );
            return;
        }
        if ("Tabel".equals(label)) {
            showActionOverlay(
                    "Tabel",
                    Arrays.asList("Tabel Utama", "Tabel Detail", "Tabel Riwayat"),
                    value -> applyResource(
                            "data.editor.table",
                            "Tabel Utama".equals(value) ? "table.main"
                                    : "Tabel Detail".equals(value) ? "table.detail"
                                    : "table.history",
                            "Tabel aktif diperbarui."
                    )
            );
            return;
        }
        if ("Kolom Data".equals(label)) {
            showActionOverlay(
                    "Kolom Data",
                    Arrays.asList("Tambah Teks", "Tambah Angka", "Tambah Boolean"),
                    value -> applyResource(
                            "data.items.field.user.type",
                            "Tambah Teks".equals(value) ? "TEXT"
                                    : "Tambah Angka".equals(value) ? "NUMBER"
                                    : "BOOLEAN",
                            "Kolom data ditambahkan ke working state."
                    )
            );
            return;
        }
        if ("Relasi".equals(label)) {
            showActionOverlay(
                    "Relasi",
                    Arrays.asList("Satu ke Satu", "Satu ke Banyak", "Tanpa Relasi"),
                    value -> applyResource(
                            "data.editor.relation",
                            "Satu ke Satu".equals(value) ? "one_to_one"
                                    : "Satu ke Banyak".equals(value) ? "one_to_many"
                                    : "none",
                            "Relasi data diperbarui."
                    )
            );
            return;
        }
        if ("Kueri".equals(label)) {
            showActionOverlay(
                    "Kueri",
                    Arrays.asList("Semua Data", "Terbaru", "10 Pertama"),
                    value -> applyResource(
                            "data.editor.query",
                            "Semua Data".equals(value) ? "all"
                                    : "Terbaru".equals(value) ? "latest"
                                    : "limit:10",
                            "Kueri aktif diperbarui."
                    )
            );
            return;
        }
        if ("Data Contoh".equals(label)) {
            String current = kernel.projectManager().current().resources().getOrDefault(
                    "data.editor.mock.enabled",
                    "default"
            );
            showActionOverlay(
                    "Data Contoh",
                    Arrays.asList(
                            mark("Aktifkan", "true".equals(current)),
                            mark("Nonaktifkan", "false".equals(current)),
                            mark("Reset", "default".equals(current))
                    ),
                    value -> {
                        String clean = stripMark(value);
                        applyResource(
                                "data.editor.mock.enabled",
                                "Aktifkan".equals(clean) ? "true"
                                        : "Nonaktifkan".equals(clean) ? "false"
                                        : "default",
                                "Data contoh diperbarui."
                        );
                    }
            );
            return;
        }
        showInfoOverlay("Data", Arrays.asList("Tidak ada aksi untuk konteks ini."));
    }

    private void showBindingEditorAction(String label) {
        if ("Hubungkan Otomatis".equals(label)) {
            String current = kernel.projectManager().current().resources().getOrDefault(
                    "binding.ui.home.primary.mode",
                    "auto"
            );
            showActionOverlay(
                    "Hubungkan Otomatis",
                    Arrays.asList(
                            mark("Hubungkan", "auto".equals(current)),
                            mark("Lepaskan", "none".equals(current))
                    ),
                    value -> applyResource(
                            "binding.ui.home.primary.mode",
                            "Hubungkan".equals(stripMark(value)) ? "auto" : "none",
                            "Status pengikatan diperbarui."
                    )
            );
            return;
        }

        String mode = kernel.projectManager().current().resources().getOrDefault(
                "binding.ui.home.primary.mode",
                "auto"
        );
        if ("Status".equals(label)) {
            showInfoOverlay("Status Pengikatan", Arrays.asList(
                    "Mode: " + ("auto".equals(mode) ? "OTOMATIS" : "TIDAK TERHUBUNG"),
                    "Target: object.home.primary",
                    "Sumber: data.items.field.title"
            ));
            return;
        }
        if ("Masalah".equals(label)) {
            showInfoOverlay("Masalah Pengikatan", Arrays.asList(
                    "Target ambigu: 0",
                    "Siklus: 0",
                    "Referensi hilang: 0"
            ));
            return;
        }
        if ("Peta".equals(label)) {
            showInfoOverlay("Peta Pengikatan", Arrays.asList(
                    "data.items.field.title → object.home.primary.property.text",
                    "Mode: " + mode
            ));
            return;
        }
        if ("Penggunaan".equals(label)) {
            showInfoOverlay("Penggunaan Pengikatan", Arrays.asList(
                    "Screen: screen.home",
                    "Objek: object.home.primary",
                    "Properti: property.text"
            ));
            return;
        }
        if ("Riwayat".equals(label)) {
            showInfoOverlay("Riwayat Pengikatan", Arrays.asList(
                    kernel.projectManager().hasUnsavedChanges()
                            ? "Ada perubahan pengikatan belum disimpan"
                            : "Tidak ada perubahan tertunda",
                    "Undo tersedia: " + (kernel.projectManager().canUndo() ? "YA" : "TIDAK"),
                    "Redo tersedia: " + (kernel.projectManager().canRedo() ? "YA" : "TIDAK")
            ));
            return;
        }
        showInfoOverlay("Pengikatan", Arrays.asList("Tidak ada aksi untuk konteks ini."));
    }

    private void showAssetEditorAction(String label) {
        if ("Kategori".equals(label)) {
            showActionOverlay(
                    "Kategori Aset",
                    Arrays.asList("Tema", "Token", "Animasi", "Kit"),
                    value -> applyResource(
                            "asset.editor.category",
                            value.toLowerCase(java.util.Locale.ROOT),
                            "Kategori aset diperbarui."
                    )
            );
            return;
        }
        if ("Impor".equals(label)) {
            String current = kernel.projectManager().current().resources().getOrDefault(
                    "asset.editor.active",
                    "asset.theme.dark.neon"
            );
            showActionOverlay(
                    "Impor Aset",
                    Arrays.asList(
                            "Pilih File dari Perangkat",
                            mark("Tema Gelap Neon", "asset.theme.dark.neon".equals(current)),
                            mark("Token Bawaan", "asset.tokens.default".equals(current)),
                            mark("Preset Animasi", "asset.animation.presets".equals(current)),
                            mark("Kit Komponen", "asset.component.kit".equals(current))
                    ),
                    value -> {
                        String clean = stripMark(value);
                        if ("Pilih File dari Perangkat".equals(clean)) {
                            closeOverlay();
                            if (getContext() instanceof WorkspaceHostActions) {
                                ((WorkspaceHostActions) getContext()).requestExternalAsset();
                            } else {
                                toast("Pemilih aset tidak tersedia pada host ini.");
                            }
                            return;
                        }
                        String id = "Tema Gelap Neon".equals(clean) ? "asset.theme.dark.neon"
                                : "Token Bawaan".equals(clean) ? "asset.tokens.default"
                                : "Preset Animasi".equals(clean) ? "asset.animation.presets"
                                : "asset.component.kit";
                        applyResource("asset.editor.active", id, "Aset terverifikasi dimuat ke working state.");
                    }
            );
            return;
        }
        if ("Pratinjau".equals(label)) {
            showActionOverlay(
                    "Pratinjau Aset",
                    Arrays.asList("Gelap Neon", "Biru Neon", "Permukaan Tenang"),
                    this::applyStylePreset
            );
            return;
        }
        if ("Penggunaan".equals(label)) {
            String activeAsset = kernel.projectManager().current().resources().getOrDefault(
                    "asset.editor.active",
                    "asset.theme.dark.neon"
            );
            showInfoOverlay("Penggunaan Aset", Arrays.asList(
                    "Aset aktif: " + activeAsset,
                    "Konsumen: tool.ui / tool.asset",
                    "Project: " + kernel.projectManager().current().projectId()
            ));
            return;
        }
        if ("Kompatibilitas".equals(label)) {
            showInfoOverlay("Kompatibilitas Aset", Arrays.asList(
                    "Android 11 / API 30: SESUAI",
                    "Runtime deklaratif: SESUAI",
                    "Kode executable: TIDAK DIPERBOLEHKAN"
            ));
            return;
        }
        if ("Dependensi".equals(label)) {
            showInfoOverlay("Dependensi Aset", Arrays.asList(
                    "Aset siap: " + kernel.libraryManager().assets().allReady().size(),
                    "Komponen siap: " + kernel.libraryManager().components().allReady().size(),
                    "Template siap: " + kernel.libraryManager().templates().allReady().size()
            ));
            return;
        }
        showInfoOverlay("Aset", Arrays.asList("Tidak ada aksi untuk konteks ini."));
    }

    private List<String> uiEditorCommands(String label) {
        if ("Gaya".equals(label)) return Arrays.asList("Gelap Neon", "Biru Neon", "Reset Gaya");
        if ("Ukuran".equals(label)) return Arrays.asList("Kecil", "Sedang", "Besar", "Lebar Penuh");
        if ("Posisi".equals(label)) return Arrays.asList("Kiri", "Tengah", "Kanan", "Reset Posisi");
        if ("Konten".equals(label)) return Arrays.asList("Buka Detail", "Simpan", "Lanjut");
        if ("Warna".equals(label)) return Arrays.asList("Neon", "Biru", "Permukaan");
        if ("Spasi".equals(label)) return Arrays.asList("Rapat", "Normal", "Lapang");
        if ("Bentuk".equals(label)) return Arrays.asList("Kotak", "Rounded", "Pill");
        if ("Garis Tepi".equals(label)) return Arrays.asList("Tanpa Garis", "Tipis", "Tebal");
        if ("Font/Teks".equals(label)) return Arrays.asList("Kecil", "Normal", "Besar");
        if ("Opasitas".equals(label)) return Arrays.asList("100%", "75%", "50%");
        if ("Rotasi/Transformasi".equals(label)) return Arrays.asList("Normal", "Putar 90°", "Skala 110%");
        if ("Perataan".equals(label)) return Arrays.asList("Kiri", "Tengah", "Kanan");
        if ("Lapisan".equals(label)) return Arrays.asList("Belakang", "Normal", "Depan");
        if ("Keadaan".equals(label)) return Arrays.asList("Aktif", "Nonaktif");
        if ("Animasi".equals(label)) return Arrays.asList("Tanpa Animasi", "Fade", "Scale");
        if ("Hubungkan Pengikatan Otomatis".equals(label)) return Arrays.asList("Hubungkan Otomatis", "Lepaskan");
        if ("Peristiwa/Aksi".equals(label)) return Arrays.asList("Buka Detail", "Tampilkan Pesan", "Tanpa Aksi");
        if ("Aksesibilitas".equals(label)) return Arrays.asList("Label Otomatis", "Label: Buka Detail");
        if ("Kunci".equals(label)) return Arrays.asList("Kunci Posisi", "Buka Kunci Posisi", "Kunci Ukuran", "Buka Kunci Ukuran");
        if ("Lainnya".equals(label)) return Arrays.asList("Reset Objek", "Simpan sebagai Template");
        return Arrays.asList("Gunakan Nilai Bawaan", "Reset");
    }

    private void applyUiEditorCommand(String label, String value) {
        if ("Gaya".equals(label) || "Warna".equals(label)) {
            applyStylePreset(value);
            return;
        }
        if ("Ukuran".equals(label)) {
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            if ("Kecil".equals(value)) {
                map.put("ui.object.home.primary.width.dp", "120");
                map.put("ui.object.home.primary.height.dp", "40");
            } else if ("Besar".equals(value)) {
                map.put("ui.object.home.primary.width.dp", "196");
                map.put("ui.object.home.primary.height.dp", "56");
            } else if ("Lebar Penuh".equals(value)) {
                map.put("ui.object.home.primary.width.dp", "260");
                map.put("ui.object.home.primary.height.dp", "48");
            } else {
                map.put("ui.object.home.primary.width.dp", "148");
                map.put("ui.object.home.primary.height.dp", "46");
            }
            applyResourceValues(map, "Ukuran diperbarui.");
            return;
        }
        if ("Posisi".equals(label) || "Perataan".equals(label)) {
            String x = "18";
            if ("Tengah".equals(value)) x = "72";
            if ("Kanan".equals(value)) x = "128";
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            map.put("ui.object.home.primary.position.x.dp", x);
            map.put("ui.object.home.primary.position.y.dp", "50");
            applyResourceValues(map, "Posisi diperbarui.");
            return;
        }
        if ("Konten".equals(label)) {
            applyResource("ui.object.home.primary.text", value, "Konten diperbarui.");
            return;
        }
        if ("Spasi".equals(label)) {
            String padding = "12";
            if ("Rapat".equals(value)) padding = "6";
            if ("Lapang".equals(value)) padding = "18";
            applyResource("ui.object.home.primary.padding.dp", padding, "Spasi diperbarui.");
            return;
        }
        if ("Bentuk".equals(label)) {
            String radius = "14";
            if ("Kotak".equals(value)) radius = "2";
            if ("Pill".equals(value)) radius = "40";
            applyResource("ui.object.home.primary.radius.dp", radius, "Bentuk diperbarui.");
            return;
        }
        if ("Garis Tepi".equals(label)) {
            String border = "1";
            if ("Tanpa Garis".equals(value)) border = "0";
            if ("Tebal".equals(value)) border = "3";
            applyResource("ui.object.home.primary.border.dp", border, "Garis tepi diperbarui.");
            return;
        }
        if ("Font/Teks".equals(label)) {
            String size = "13";
            if ("Kecil".equals(value)) size = "11";
            if ("Besar".equals(value)) size = "17";
            applyResource("ui.object.home.primary.text.size.sp", size, "Teks diperbarui.");
            return;
        }
        if ("Opasitas".equals(label)) {
            String alpha = "1.0";
            if ("75%".equals(value)) alpha = "0.75";
            if ("50%".equals(value)) alpha = "0.5";
            applyResource("ui.object.home.primary.opacity", alpha, "Opasitas diperbarui.");
            return;
        }
        if ("Rotasi/Transformasi".equals(label)) {
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            map.put("ui.object.home.primary.rotation", "Putar 90°".equals(value) ? "90" : "0");
            map.put("ui.object.home.primary.scale", "Skala 110%".equals(value) ? "1.1" : "1.0");
            applyResourceValues(map, "Transformasi diperbarui.");
            return;
        }
        if ("Lapisan".equals(label)) {
            String elevation = "4";
            if ("Belakang".equals(value)) elevation = "0";
            if ("Depan".equals(value)) elevation = "12";
            applyResource("ui.object.home.primary.elevation.dp", elevation, "Lapisan diperbarui.");
            return;
        }
        if ("Keadaan".equals(label)) {
            applyResource(
                    "ui.object.home.primary.enabled",
                    "Aktif".equals(value) ? "true" : "false",
                    "Keadaan diperbarui."
            );
            return;
        }
        if ("Animasi".equals(label)) {
            String animation = "none";
            if ("Fade".equals(value)) animation = "fade";
            if ("Scale".equals(value)) animation = "scale";
            applyResource("ui.object.home.primary.animation", animation, "Pratinjau animasi diperbarui.");
            return;
        }
        if ("Hubungkan Pengikatan Otomatis".equals(label)) {
            applyResource(
                    "binding.ui.home.primary.mode",
                    "Hubungkan Otomatis".equals(value) ? "auto" : "none",
                    "Status pengikatan diperbarui."
            );
            return;
        }
        if ("Peristiwa/Aksi".equals(label)) {
            String action = "none";
            if ("Buka Detail".equals(value)) action = "open.detail";
            if ("Tampilkan Pesan".equals(value)) action = "show.message";
            applyResource("logic.ui.home.primary.action", action, "Aksi diperbarui.");
            return;
        }
        if ("Aksesibilitas".equals(label)) {
            applyResource(
                    "ui.object.home.primary.accessibility.label",
                    "Buka Detail",
                    "Aksesibilitas diperbarui."
            );
            return;
        }
        if ("Kunci".equals(label)) {
            boolean locked = value.startsWith("Kunci");
            VisualCapability cap = value.contains("Ukuran")
                    ? VisualCapability.SIZE
                    : VisualCapability.POSITION;
            try {
                kernel.editorEnvironment().visualSession().setLocked(
                        "object.home.primary",
                        cap,
                        locked
                );
                closeOverlay();
                toast(locked ? "Area edit dikunci." : "Area edit dibuka.");
            } catch (RuntimeException error) {
                toast("Objek belum tersedia untuk dikunci.");
            }
            return;
        }
        if ("Lainnya".equals(label)) {
            if ("Reset Objek".equals(value)) {
                resetPrimaryObject();
            } else {
                applyResource(
                        "template.user.primary.label",
                        kernel.projectManager().current().resources().getOrDefault(
                                "ui.object.home.primary.text",
                                "Buka Detail"
                        ),
                        "Template pengguna tersimpan."
                );
            }
            return;
        }
        resetPrimaryObject();
    }

    private void applyTemplate(String value) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if ("Layar Formulir".equals(value)) {
            map.put("ui.screen.home.title", "Formulir Baru");
            map.put("ui.screen.home.subtitle", "Masukkan data lalu lanjutkan.");
            map.put("ui.object.home.primary.text", "Lanjut");
        } else if ("Reset Template".equals(value)) {
            map.put("ui.screen.home.title", "Bangun aplikasi secara visual");
            map.put("ui.screen.home.subtitle", "Layar ini adalah permukaan yang sama saat Edit aktif maupun nonaktif.");
            map.put("ui.object.home.primary.text", "Buka Detail");
        } else {
            map.put("ui.screen.home.title", "Layar Dasar");
            map.put("ui.screen.home.subtitle", "Template dasar siap diedit.");
            map.put("ui.object.home.primary.text", "Mulai");
        }
        applyResourceValues(map, "Template diterapkan.");
    }

    private void applyStylePreset(String value) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if ("Biru Neon".equals(value) || "Biru".equals(value)) {
            map.put("ui.object.home.primary.color", "blue");
            map.put("ui.object.home.primary.radius.dp", "14");
        } else if ("Permukaan Tenang".equals(value) || "Permukaan".equals(value)) {
            map.put("ui.object.home.primary.color", "surface");
            map.put("ui.object.home.primary.radius.dp", "12");
        } else {
            map.put("ui.object.home.primary.color", "neon");
            map.put("ui.object.home.primary.radius.dp", "14");
        }
        applyResourceValues(map, "Gaya diterapkan.");
    }

    private void resetPrimaryObject() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("ui.object.home.primary.text", "Buka Detail");
        map.put("ui.object.home.primary.width.dp", "148");
        map.put("ui.object.home.primary.height.dp", "46");
        map.put("ui.object.home.primary.position.x.dp", "18");
        map.put("ui.object.home.primary.position.y.dp", "50");
        map.put("ui.object.home.primary.padding.dp", "12");
        map.put("ui.object.home.primary.radius.dp", "14");
        map.put("ui.object.home.primary.border.dp", "1");
        map.put("ui.object.home.primary.text.size.sp", "13");
        map.put("ui.object.home.primary.opacity", "1.0");
        map.put("ui.object.home.primary.rotation", "0");
        map.put("ui.object.home.primary.scale", "1.0");
        map.put("ui.object.home.primary.elevation.dp", "4");
        map.put("ui.object.home.primary.enabled", "true");
        map.put("ui.object.home.primary.animation", "none");
        map.put("ui.object.home.primary.color", "neon");
        map.put("logic.ui.home.primary.action", "open.detail");
        applyResourceValues(map, "Objek dikembalikan ke nilai bawaan.");
    }

    private void applyResource(String key, String value, String success) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        applyResourceValues(map, success);
    }

    private void applyResourceValues(Map<String, String> values, String success) {
        try {
            LinkedHashMap<String, String> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                mapped.put(
                        remapUiResourceKey(entry.getKey()),
                        entry.getValue()
                );
            }
            kernel.projectManager().applyResourceTransaction(
                    mapped,
                    Collections.emptySet()
            );
            closeOverlay();
            renderWorkspace();
            renderEdge();
            toast(success);
        } catch (RuntimeException error) {
            toast("Perubahan ditolak secara aman.");
        }
    }

    private void saveProject() {
        try {
            kernel.projectManager().save();
            toast("Proyek tersimpan secara transaksional.");
            renderWorkspace();
            renderEdge();
        } catch (Exception error) {
            toast("Simpan gagal aman. Revisi valid sebelumnya tetap dipertahankan.");
        }
    }

    private void undo() {
        boolean visual = kernel.editorEnvironment().visualSession().undo();
        boolean project = kernel.projectManager().undo();
        toast((visual || project)
                ? "Perubahan diurungkan."
                : "Tidak ada perubahan untuk diurungkan.");
        renderWorkspace();
        renderEdge();
    }

    private void redo() {
        boolean visual = kernel.editorEnvironment().visualSession().redo();
        boolean project = kernel.projectManager().redo();
        toast((visual || project)
                ? "Perubahan diulangi."
                : "Tidak ada perubahan untuk diulangi.");
        renderWorkspace();
        renderEdge();
    }

    private void showProjectInfo() {
        showInfoOverlay(
                "Proyek & File",
                Arrays.asList(
                        "Project: " + kernel.projectManager().current().projectId(),
                        "Revisi tersimpan: " + kernel.projectManager().savedRevision(),
                        "Perubahan tertunda: " + (kernel.projectManager().hasUnsavedChanges() ? "YA" : "TIDAK"),
                        "Status akses: " + kernel.projectManager().accessStatus().name()
                )
        );
    }

    private void showSettings() {
        String storageStatus = getContext() instanceof StoragePickerHost
                ? ((StoragePickerHost) getContext()).storageTreeStatus()
                : (kernel.productServices().completion().storage.hasPersistentReadWriteGrant()
                        ? "Terhubung"
                        : "Belum dipilih");
        showActionOverlay(
                "Pengaturan",
                Arrays.asList(
                        "Bahasa • Bahasa Indonesia",
                        "Tema • Gelap Neon",
                        "Representasi Default • Visual",
                        "Penyimpanan Pengguna • " + storageStatus,
                        "Pilih / Relink Folder ToolBox",
                        "Autosave • NONAKTIF",
                        "Simpan • Manual Transaksional",
                        "Target • Android 11 / API 30 / arm64-v8a"
                ),
                value -> {
                    if ("Pilih / Relink Folder ToolBox".equals(value)) {
                        closeOverlay();
                        if (getContext() instanceof StoragePickerHost) {
                            ((StoragePickerHost) getContext()).requestToolBoxStorageTree();
                        } else {
                            toast("Pemilih folder tidak tersedia pada host ini.");
                        }
                    }
                }
        );
    }

    private void showFreeze() {
        FreezeEngine freeze = kernel.productServices().freeze();
        showActionOverlay(
                "Freeze & Mode Simpan",
                Arrays.asList(
                        "Mode Normal",
                        "Mode Simpan • Titik Pemeriksaan",
                        "Mode Simpan • Pemulihan",
                        "Commit Baseline Kerja",
                        "Thaw / Kembali Normal"
                ),
                value -> {
                    try {
                        if ("Mode Simpan • Titik Pemeriksaan".equals(value)) {
                            if (freeze.state() == FreezeEngine.State.NORMAL) freeze.freeze();
                            else if (freeze.state() == FreezeEngine.State.FROZEN) freeze.commit();
                            toast("Titik pemeriksaan aktif.");
                        } else if ("Mode Simpan • Pemulihan".equals(value)) {
                            if (freeze.state() == FreezeEngine.State.FROZEN) freeze.recover();
                            toast("Pemulihan selesai.");
                        } else if ("Commit Baseline Kerja".equals(value)) {
                            if (freeze.state() == FreezeEngine.State.FROZEN) freeze.commit();
                            toast("Baseline kerja diperbarui.");
                        } else {
                            if (freeze.state() == FreezeEngine.State.FROZEN) freeze.thaw();
                            toast("Mode normal aktif.");
                        }
                        closeOverlay();
                    } catch (Exception error) {
                        toast("Operasi Freeze gagal aman.");
                    }
                }
        );
    }

    private void showHealth() {
        HealthReport report = kernel.healthMonitor().inspect(kernel);
        showActionOverlay(
                "Kesehatan & Perbaikan",
                Arrays.asList(
                        "Status • " + (report.isHealthy() ? "SEHAT" : "PERLU PERHATIAN"),
                        "Jalankan Health Check",
                        kernel.safeModeController().isSafeMode()
                                ? "Keluar Mode Aman"
                                : "Masuk Mode Aman",
                        "Buang Perubahan Kerja",
                        "Diagnostik Lengkap"
                ),
                value -> {
                    if ("Jalankan Health Check".equals(value)) {
                        HealthReport fresh = kernel.healthMonitor().inspect(kernel);
                        closeOverlay();
                        showInfoOverlay(
                                "Hasil Health Check",
                                Arrays.asList(
                                        "Status: " + fresh.state().name(),
                                        "Alasan: " + fresh.reasons(),
                                        "Kernel: " + kernel.state().name(),
                                        "Project: " + kernel.projectManager().accessStatus().name(),
                                        "Runtime model: "
                                                + (kernel.runtimeEnvironment() != null ? "SIAP" : "GAGAL"),
                                        "Safe Mode: " + kernel.safeModeController().statusIndonesia()
                                )
                        );
                    } else if ("Masuk Mode Aman".equals(value)) {
                        kernel.safeModeController().enter();
                        closeOverlay();
                        toast("Mode aman aktif • inspeksi read-only disarankan.");
                    } else if ("Keluar Mode Aman".equals(value)) {
                        try {
                            kernel.safeModeController().exitIfHealthy();
                            closeOverlay();
                            toast("Mode normal aktif.");
                        } catch (RuntimeException error) {
                            toast("Belum dapat keluar: pemulihan masih diperlukan.");
                        }
                    } else if ("Buang Perubahan Kerja".equals(value)) {
                        try {
                            kernel.safeModeController().discardWorkingChanges();
                            closeOverlay();
                            renderAll();
                            toast("Working state dikembalikan ke revisi tersimpan.");
                        } catch (IOException error) {
                            toast("Pemulihan working state gagal aman.");
                        }
                    } else if ("Diagnostik Lengkap".equals(value)) {
                        closeOverlay();
                        showDiagnostics();
                    }
                }
        );
    }

    private void showBuild() {
        FullProductVerifier.Result product = new FullProductVerifier().verify(kernel);
        ProductAcceptanceMatrix.Result acceptance =
                new ProductAcceptanceMatrix().evaluate(kernel);
        boolean buildReady = kernel.readyCoordinator().preview().isPass();
        showInfoOverlay(
                "Bangun & SIAP",
                Arrays.asList(
                        "Kelengkapan Produk: " + (product.isPass() ? "LULUS" : "BELUM LULUS"),
                        "Rancangan Behavior: " + acceptance.passedCount()
                                + "/" + acceptance.requiredCount(),
                        "Komponen Wajib: " + product.available().size() + "/" + product.requiredCount(),
                        "Validator Build: " + (buildReady ? "LULUS" : "BLOKIR"),
                        "IR Kanonik: tersedia",
                        "Signing: hanya jalur Private",
                        "Firebase: hanya setelah izin pengguna"
                )
        );
    }

    private void showDiagnostics() {
        FullProductVerifier.Result result = new FullProductVerifier().verify(kernel);
        ProductAcceptanceMatrix.Result acceptance =
                new ProductAcceptanceMatrix().evaluate(kernel);
        showInfoOverlay(
                "Diagnostik",
                Arrays.asList(
                        "Kelengkapan: " + (result.isPass() ? "LULUS" : "GAGAL"),
                        "Rancangan Behavior: " + acceptance.passedCount()
                                + "/" + acceptance.requiredCount(),
                        "Bagian gagal: " + acceptance.failedSections(),
                        "Masalah wajib: " + result.errors().size(),
                        "Engine aktif: " + kernel.engineManager().snapshot().size(),
                        "Komponen siap: " + kernel.libraryManager().components().allReady().size(),
                        "Aset siap: " + kernel.libraryManager().assets().allReady().size(),
                        "Template siap: " + kernel.libraryManager().templates().allReady().size()
                )
        );
    }

    private void showRecovery() {
        try {
            int candidates = kernel.projectManager().recoveryCandidates().size();
            showActionOverlay(
                    "Pemulihan & Backup",
                    Arrays.asList(
                            "Buat Backup Terverifikasi",
                            "Lihat Kandidat Pemulihan"
                    ),
                    value -> {
                        if ("Buat Backup Terverifikasi".equals(value)) {
                            try {
                                kernel.productServices().backups().createVerified();
                                closeOverlay();
                                toast("Backup terverifikasi dibuat.");
                            } catch (Exception error) {
                                toast("Backup gagal dibuat.");
                            }
                        } else {
                            closeOverlay();
                            showInfoOverlay(
                                    "Kandidat Pemulihan",
                                    Arrays.asList(
                                            "Jumlah kandidat: " + candidates,
                                            "Titik pemulihan final: didukung",
                                            "Riwayat revisi: didukung"
                                    )
                            );
                        }
                    }
            );
        } catch (IOException error) {
            toast("Daftar pemulihan tidak dapat dibaca.");
        }
    }

    private void showEvolution() {
        String packageStatus = getContext() instanceof WorkspaceHostActions
                ? ((WorkspaceHostActions) getContext()).evolutionPackageStatus()
                : kernel.evolutionManager().state().name();
        showActionOverlay(
                "Evolusi Tanpa Rebuild",
                Arrays.asList(
                        "Status • " + kernel.evolutionManager().state().name(),
                        "Pilih Paket app.patch",
                        "Pratinjau • " + packageStatus,
                        "Terapkan Paket Terverifikasi",
                        "Rollback ke Revisi Dasar",
                        "Batas • kode executable memerlukan APK baru"
                ),
                value -> {
                    if ("Pilih Paket app.patch".equals(value)) {
                        closeOverlay();
                        if (getContext() instanceof WorkspaceHostActions) {
                            ((WorkspaceHostActions) getContext())
                                    .requestEvolutionPackage();
                        } else {
                            toast("Pemilih app.patch tidak tersedia.");
                        }
                    } else if ("Terapkan Paket Terverifikasi".equals(value)) {
                        try {
                            com.toolbox.tools.delivery.PatchApplyResult result =
                                    kernel.evolutionManager().apply();
                            closeOverlay();
                            renderAll();
                            HealthReport health =
                                    kernel.healthMonitor().inspect(kernel);
                            toast(
                                    result.state().name()
                                            + " • health "
                                            + health.state().name()
                            );
                        } catch (RuntimeException error) {
                            toast("Paket belum berada pada state siap apply.");
                        }
                    } else if ("Rollback ke Revisi Dasar".equals(value)) {
                        try {
                            com.toolbox.tools.delivery.PatchApplyResult result =
                                    kernel.evolutionManager().rollback();
                            closeOverlay();
                            renderAll();
                            toast("Rollback: " + result.state().name());
                        } catch (RuntimeException error) {
                            toast("Tidak ada revisi dasar yang dapat di-rollback.");
                        }
                    } else if (value.startsWith("Pratinjau")) {
                        closeOverlay();
                        showInfoOverlay(
                                "Pratinjau Evolusi",
                                Arrays.asList(
                                        packageStatus,
                                        "Signature remote: wajib dan diverifikasi sebelum staging siap",
                                        "Recovery snapshot: dibuat sebelum mutasi",
                                        "Apply: transaksional",
                                        "Failure: restore / failed-safe"
                                )
                        );
                    }
                }
        );
    }

    private void showActionOverlay(
            String title,
            List<String> rows,
            CommandHandler handler
    ) {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(VISIBLE);
        overlayLayer.bringToFront();
        bubble.bringToFront();
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
        TextView titleView = UiKit.judul(getContext(), title, 17f);
        titleView.setTextColor(UiKit.NEON);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0,
                UiKit.dp(getContext(), 42),
                1
        ));
        TextView close = UiKit.chip(getContext(), "Tutup", false);
        close.setOnClickListener(v -> closeOverlay());
        header.addView(close);
        card.addView(header);

        makeHeaderDraggable(titleView, scroll);

        for (String row : rows) {
            TextView item = UiKit.tombol(getContext(), row, false);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setTextSize(12f);
            item.setOnClickListener(v -> handler.onCommand(row));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    UiKit.dp(getContext(), 46)
            );
            p.bottomMargin = UiKit.dp(getContext(), 6);
            card.addView(item, p);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(
                        UiKit.dp(getContext(), 340),
                        getResources().getDisplayMetrics().widthPixels - UiKit.dp(getContext(), 32)
                ),
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlayLayer.addView(scroll, params);
    }

    private void showInfoOverlay(String title, List<String> rows) {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(VISIBLE);
        overlayLayer.bringToFront();
        bubble.bringToFront();
        overlayLayer.setBackgroundColor(Color.argb(150, 0, 0, 0));
        overlayLayer.setOnClickListener(v -> closeOverlay());

        LinearLayout card = UiKit.kolom(getContext());
        card.setPadding(
                UiKit.dp(getContext(), 16),
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 16),
                UiKit.dp(getContext(), 16)
        );
        card.setBackground(UiKit.kartuPx(
                getContext(),
                Color.rgb(9, 26, 34),
                UiKit.NEON_BIRU,
                22,
                1
        ));
        card.setOnClickListener(v -> {});

        LinearLayout header = UiKit.baris(getContext());
        TextView titleView = UiKit.judul(getContext(), title, 17f);
        titleView.setTextColor(UiKit.NEON_BIRU);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0,
                UiKit.dp(getContext(), 42),
                1
        ));
        TextView close = UiKit.chip(getContext(), "Tutup", false);
        close.setOnClickListener(v -> closeOverlay());
        header.addView(close);
        card.addView(header);

        makeHeaderDraggable(titleView, card);

        for (String row : rows) {
            TextView item = UiKit.teks(getContext(), row, 12f, UiKit.TEKS);
            item.setPadding(
                    UiKit.dp(getContext(), 8),
                    UiKit.dp(getContext(), 8),
                    UiKit.dp(getContext(), 8),
                    UiKit.dp(getContext(), 8)
            );
            card.addView(item);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(
                        UiKit.dp(getContext(), 340),
                        getResources().getDisplayMetrics().widthPixels - UiKit.dp(getContext(), 32)
                ),
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlayLayer.addView(card, params);
    }

    private void makeHeaderDraggable(View handle, View card) {
        final float[] down = new float[4];
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    down[2] = card.getX();
                    down[3] = card.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float nx = down[2] + event.getRawX() - down[0];
                    float ny = down[3] + event.getRawY() - down[1];
                    float maxX = Math.max(0, getWidth() - card.getWidth());
                    float maxY = Math.max(0, getHeight() - card.getHeight());
                    card.setX(Math.max(0, Math.min(maxX, nx)));
                    card.setY(Math.max(0, Math.min(maxY, ny)));
                    return true;
                default:
                    return true;
            }
        });
    }

    private void closeOverlay() {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(GONE);
        overlayLayer.setBackgroundColor(Color.TRANSPARENT);
    }

    private void activateToolSection(
            AuthoringSection section
    ) {
        kernel.authoringWorkspace().activate(section);
        kernel.productServices().resources().activate(section);
        kernel.productServices().toolLifecycle().activate(
                section == AuthoringSection.UI
                        ? "tool.ui"
                        : section == AuthoringSection.LOGIC
                        ? "tool.logic"
                        : section == AuthoringSection.DATA
                        ? "tool.data"
                        : section == AuthoringSection.BINDING
                        ? "tool.binding"
                        : "tool.asset"
        );
    }

    private void showInstalledTargets() {
        List<ProductCompletionServices.InstalledTargetBridge.Target> targets =
                kernel.productServices().completion().installedTargets.all();
        if (targets.isEmpty()) {
            showInfoOverlay(
                    "Aplikasi Terinstal",
                    Arrays.asList(
                            "Tidak ada target ToolBox-aware yang ditemukan.",
                            "Target harus mendeklarasikan intent com.toolbox.AWARE.",
                            "Sandbox dan signature aplikasi tetap dihormati."
                    )
            );
            return;
        }
        List<String> rows = new ArrayList<>();
        for (ProductCompletionServices.InstalledTargetBridge.Target target : targets) {
            rows.add(target.label() + " • " + target.packageName());
        }
        showActionOverlay("Pilih Target ToolBox-aware", rows, value -> {
            for (ProductCompletionServices.InstalledTargetBridge.Target target : targets) {
                String row = target.label() + " • " + target.packageName();
                if (!row.equals(value)) continue;
                try {
                    ManagedAppProtocol.Descriptor descriptor =
                            new ManagedAppProtocol.Descriptor(
                                    target.packageName(),
                                    target.protocolVersion(),
                                    kernel.productServices()
                                            .managedAppProtocol()
                                            .parseCapabilities(
                                                    target.capabilities()
                                            ),
                                    target.projectId(),
                                    target.revision()
                            );
                    ManagedAppProtocol.Session session =
                            kernel.productServices()
                                    .managedAppProtocol()
                                    .negotiate(
                                            descriptor,
                                            descriptor.capabilities()
                                    );
                    activeTargetPackage = target.packageName();
                    activeTargetSession = session.sessionId();
                    LinkedHashMap<String, String> metadata =
                            new LinkedHashMap<>();
                    metadata.put(
                            "target.active.package",
                            target.packageName()
                    );
                    metadata.put(
                            "target.active.session",
                            session.sessionId()
                    );
                    metadata.put(
                            "target.active.protocol",
                            Integer.toString(
                                    target.protocolVersion()
                            )
                    );
                    metadata.put(
                            "target.active.project",
                            target.projectId()
                    );
                    metadata.put(
                            "target.active.revision",
                            Long.toString(target.revision())
                    );
                    metadata.put(
                            "target.active.capabilities",
                            android.text.TextUtils.join(
                                    ",",
                                    target.capabilities()
                            )
                    );
                    kernel.projectManager().applyResourceTransaction(
                            metadata,
                            Collections.emptySet()
                    );
                } catch (RuntimeException error) {
                    toast("Target gagal dinegosiasikan secara aman.");
                    return;
                }
                closeOverlay();
                openEditor(
                        "Aplikasi Terinstal • " + target.label()
                );
                return;
            }
        });
    }

    private String remapUiResourceKey(String key) {
        if (active != AuthoringSection.UI) return key;
        String selected = kernel.editorEnvironment().shell().selectedObjectId();
        if (selected == null || "object.home.primary".equals(selected)) return key;
        String canonical = "ui.object.home.primary.";
        if (key.startsWith(canonical)) {
            return selected + "." + key.substring(canonical.length());
        }
        return key;
    }

    private String stateHoldId() {
        return screen == Screen.HOME
                ? "screen.home"
                : screen == Screen.EDITOR_CHOOSER
                ? "screen.editor.chooser"
                : "screen.editor.workspace";
    }

    private void applyProgressiveEdgeDrag(MotionEvent start, MotionEvent now) {
        if (start == null || now == null) return;
        float extent = isLandscape()
                ? Math.max(1, edgeContainer.getHeight())
                : Math.max(1, edgeContainer.getWidth());
        float delta = isLandscape()
                ? now.getRawY() - start.getRawY()
                : now.getRawX() - start.getRawX();
        float hidden;
        if (isLandscape()) {
            boolean bottom = edgeAnchor == EdgeAnchor.BOTTOM;
            hidden = bottom ? extent : -extent;
            float base = edgeOpen ? 0f : hidden;
            float requested = base + delta;
            float min = bottom ? 0f : -extent;
            float max = bottom ? extent : 0f;
            float value = Math.max(min, Math.min(max, requested));
            edgeContainer.setTranslationY(value);
        } else {
            boolean right = edgeAnchor == EdgeAnchor.RIGHT;
            hidden = right ? extent : -extent;
            float base = edgeOpen ? 0f : hidden;
            float requested = base + delta;
            float min = right ? 0f : -extent;
            float max = right ? extent : 0f;
            float value = Math.max(min, Math.min(max, requested));
            edgeContainer.setTranslationX(value);
        }
    }

    private void snapProgressiveEdgeDrag() {
        float extent = isLandscape()
                ? Math.max(1, edgeContainer.getHeight())
                : Math.max(1, edgeContainer.getWidth());
        float hiddenDistance = isLandscape()
                ? Math.abs(edgeContainer.getTranslationY())
                : Math.abs(edgeContainer.getTranslationX());
        setEdgeOpen(hiddenDistance < extent * 0.5f);
        edgeDragActive = false;
    }

    private void clampBubbleToSafeArea() {
        if (bubble.getWidth() <= 0 || bubble.getHeight() <= 0) return;
        float minX = systemInsets.left;
        float minY = systemInsets.top;
        float maxX = Math.max(minX, getWidth() - systemInsets.right - bubble.getWidth());
        float maxY = Math.max(minY, getHeight() - systemInsets.bottom - bubble.getHeight());
        bubble.setX(Math.max(minX, Math.min(maxX, bubble.getX())));
        bubble.setY(Math.max(minY, Math.min(maxY, bubble.getY())));
    }

    private void sampleRuntimeResources() {
        String screenId = screen == Screen.HOME
                ? "screen.home"
                : "screen.editor.workspace";
        kernel.productServices().resources().enterScreen(screenId);
        kernel.productServices().resources().sample(
                screenId,
                Debug.getPss() * 1024L,
                countViews(this),
                countHeavyViews(this)
        );
    }

    private static int countViews(View view) {
        if (!(view instanceof ViewGroup)) return 1;
        int count = 1;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            count += countViews(group.getChildAt(i));
        }
        return count;
    }

    private static int countHeavyViews(View view) {
        int count = view instanceof android.widget.ImageView
                || view instanceof android.widget.VideoView
                ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countHeavyViews(group.getChildAt(i));
            }
        }
        return count;
    }

    public void runSoakForTest(int cycles) {
        if (cycles < 1 || cycles > 200) {
            throw new IllegalArgumentException("cycles out of range");
        }
        long before = Debug.getPss() * 1024L;
        Screen previousScreen = screen;
        AuthoringSection previous = active;
        screen = Screen.EDITOR_WORKSPACE;
        AuthoringSection[] sections = AuthoringSection.values();
        for (int i = 0; i < cycles; i++) {
            active = sections[i % sections.length];
            activateToolSection(active);
            renderWorkspace();
        }
        active = previous;
        screen = previousScreen;
        activateToolSection(active);
        renderAll();
        System.gc();
        long after = Debug.getPss() * 1024L;
        lastSoakPssDriftBytes = Math.max(0, after - before);
    }

    public long lastSoakPssDriftBytesForTest() {
        return lastSoakPssDriftBytes;
    }

    private void addInfo(LinearLayout parent, String title, String detail) {
        TextView a = UiKit.judul(getContext(), title, 13f);
        a.setTextColor(UiKit.TEKS);
        parent.addView(a);
        TextView b = UiKit.teks(getContext(), detail, 11.5f, UiKit.TEKS_REDUP);
        b.setPadding(0, UiKit.dp(getContext(), 2), 0, UiKit.dp(getContext(), 10));
        parent.addView(b);
    }

    private LinearLayout card() {
        LinearLayout card = UiKit.kolom(getContext());
        card.setPadding(
                UiKit.dp(getContext(), 16),
                UiKit.dp(getContext(), 16),
                UiKit.dp(getContext(), 16),
                UiKit.dp(getContext(), 16)
        );
        card.setBackground(UiKit.kartuPx(
                getContext(),
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                18,
                1
        ));
        return card;
    }

    private String labelScreen() {
        switch (screen) {
            case HOME: return "Antarmuka ToolBox";
            case EDITOR_CHOOSER: return "Pilihan Editor";
            case EDITOR_WORKSPACE:
            default: return "Editor";
        }
    }

    private static String contextDisplayLabel(
            AuthoringSection section,
            String actionLabel
    ) {
        if (section == AuthoringSection.ASSET && "Impor".equals(actionLabel)) {
            return "Impor Aset";
        }
        if (section == AuthoringSection.DATA && "Data Contoh".equals(actionLabel)) {
            return "Kelola Data Contoh";
        }
        return actionLabel;
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

    private static String mark(String label, boolean active) {
        return active ? "✓ " + label : label;
    }

    private static String stripMark(String value) {
        return value != null && value.startsWith("✓ ")
                ? value.substring(2)
                : value;
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

    private void persistBubblePosition(View view) {
        preferences().edit()
                .putFloat("bubble.x." + orientationSuffix(), view.getX())
                .putFloat("bubble.y." + orientationSuffix(), view.getY())
                .apply();
    }

    private void restoreBubblePosition() {
        float x = preferences().getFloat(
                "bubble.x." + orientationSuffix(),
                UiKit.dp(getContext(), 16)
        );
        float y = preferences().getFloat(
                "bubble.y." + orientationSuffix(),
                UiKit.dp(getContext(), 100)
        );
        float maxX = Math.max(0, getWidth() - bubble.getWidth());
        float maxY = Math.max(0, getHeight() - bubble.getHeight());
        bubble.setX(Math.max(0, Math.min(maxX, x)));
        bubble.setY(Math.max(0, Math.min(maxY, y)));
    }

    private SharedPreferences preferences() {
        return getContext().getSharedPreferences(
                "toolbox.shell",
                Context.MODE_PRIVATE
        );
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private EdgeAnchor restoreEdgeAnchor() {
        String key = "edge.anchor." + orientationSuffix();
        String fallback = isLandscape() ? "BOTTOM" : "RIGHT";
        String saved = preferences().getString(key, fallback);
        try {
            EdgeAnchor result = EdgeAnchor.valueOf(saved);
            if (isLandscape()) {
                return result == EdgeAnchor.TOP || result == EdgeAnchor.BOTTOM
                        ? result
                        : EdgeAnchor.BOTTOM;
            }
            return result == EdgeAnchor.LEFT || result == EdgeAnchor.RIGHT
                    ? result
                    : EdgeAnchor.RIGHT;
        } catch (IllegalArgumentException error) {
            return isLandscape() ? EdgeAnchor.BOTTOM : EdgeAnchor.RIGHT;
        }
    }

    private void persistEdgeAnchor() {
        preferences().edit()
                .putString("edge.anchor." + orientationSuffix(), edgeAnchor.name())
                .apply();
    }

    private String edgeAnchorLabel() {
        switch (edgeAnchor) {
            case LEFT: return "KIRI";
            case RIGHT: return "KANAN";
            case TOP: return "ATAS";
            case BOTTOM:
            default: return "BAWAH";
        }
    }

    private String orientationSuffix() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE
                ? "landscape"
                : "portrait";
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private void toast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private interface CommandHandler {
        void onCommand(String value);
    }
}
