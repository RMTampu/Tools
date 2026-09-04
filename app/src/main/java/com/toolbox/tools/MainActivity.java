package com.toolbox.tools;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.toolbox.tools.authoring.AuthoringSearchResult;
import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.VerificationManager;
import com.toolbox.tools.editor.EditorFunction;
import com.toolbox.tools.editor.EdgeItem;
import com.toolbox.tools.editor.EdgePanelModel;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.integration.ExportPackage;
import com.toolbox.tools.integration.ExternalSnapshot;
import com.toolbox.tools.integration.NormalizationResult;
import com.toolbox.tools.integration.SyncPlan;
import com.toolbox.tools.live.CapabilityArea;
import com.toolbox.tools.live.CapabilityScanResult;
import com.toolbox.tools.live.LiveApplyResult;
import com.toolbox.tools.live.LiveChange;
import com.toolbox.tools.live.LiveChangeOperation;
import com.toolbox.tools.live.LiveSessionState;
import com.toolbox.tools.repair.HealthReport;
import com.toolbox.tools.repair.RepairPlan;
import com.toolbox.tools.repair.RepairValidationResult;

import java.io.File;

public final class MainActivity extends Activity {
    private AppKernel kernel;
    private FrameLayout root;
    private LinearLayout edgePanel;
    private ScrollView edgeScroll;
    private LinearLayout floatingEditor;
    private TextView canvasObject;
    private TextView bubble;
    private LinearLayout authoringBar;
    private TextView authoringStatus;
    private TextView integrationStatus;
    private TextView repairStatus;
    private TextView liveStatus;
    private float downRawX;
    private float downRawY;
    private float startX;
    private float startY;
    private boolean dragging;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File projectRoot = new File(getFilesDir(), "projects/project.default");
        File assetLibraryRoot = new File(getFilesDir(), "library/assets");
        kernel = AppKernel.createPersistent(projectRoot, assetLibraryRoot);
        String status = new VerificationManager().verify(kernel).isPass()
                ? "LULUS"
                : "GAGAL";

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 12, 18));

        TextView header = label(
                "ToolBox Tahap 9\nCapability Scan • Live • TERAPKAN • Self Edit\nRepair • Recovery • UI • Logic • Data • Binding • Asset\n" + status,
                18f
        );
        header.setGravity(Gravity.CENTER);
        header.setTextColor(Color.rgb(220, 255, 245));
        root.addView(
                header,
                frameMatch()
        );

        canvasObject = label("Buka Detail", 18f);
        canvasObject.setGravity(Gravity.CENTER);
        canvasObject.setTextColor(Color.WHITE);
        canvasObject.setBackground(rounded(
                Color.rgb(25, 45, 58),
                Color.rgb(0, 255, 190),
                18
        ));
        FrameLayout.LayoutParams objectParams =
                new FrameLayout.LayoutParams(dp(180), dp(64));
        objectParams.gravity = Gravity.CENTER;
        root.addView(canvasObject, objectParams);
        canvasObject.setOnClickListener(v -> {
            if (!kernel.editorEnvironment().shell().editEnabled()) return;
            kernel.editorEnvironment().shell().selectObject(
                    "object.home.primary"
            );
            renderEdge();
        });

        edgePanel = new LinearLayout(this);
        edgePanel.setOrientation(LinearLayout.VERTICAL);
        edgePanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        edgePanel.setBackground(rounded(
                Color.rgb(15, 25, 35),
                Color.rgb(0, 255, 190),
                16
        ));
        edgeScroll = new ScrollView(this);
        edgeScroll.setFillViewport(true);
        edgeScroll.addView(
                edgePanel,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        FrameLayout.LayoutParams edgeParams =
                new FrameLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.MATCH_PARENT);
        edgeParams.gravity = Gravity.END;
        edgeParams.topMargin = dp(24);
        edgeParams.bottomMargin = dp(24);
        edgeScroll.setVisibility(View.GONE);
        root.addView(edgeScroll, edgeParams);

        floatingEditor = new LinearLayout(this);
        floatingEditor.setOrientation(LinearLayout.VERTICAL);
        floatingEditor.setPadding(dp(12), dp(12), dp(12), dp(12));
        floatingEditor.setBackground(rounded(
                Color.rgb(22, 32, 44),
                Color.rgb(90, 170, 255),
                14
        ));
        floatingEditor.addView(label("Floating Editor • Size", 16f));
        TextView sizeValue = label("Width 180 • Height 64\nX = tutup tanpa revert", 14f);
        floatingEditor.addView(sizeValue);
        TextView close = label("X Tutup", 14f);
        close.setOnClickListener(v -> {
            kernel.editorEnvironment().floatingEditor().close();
            floatingEditor.setVisibility(View.GONE);
        });
        floatingEditor.addView(close);
        FrameLayout.LayoutParams floatingParams =
                new FrameLayout.LayoutParams(dp(220), dp(150));
        floatingParams.leftMargin = dp(24);
        floatingParams.topMargin = dp(140);
        floatingEditor.setVisibility(View.GONE);
        root.addView(floatingEditor, floatingParams);

        bubble = label("TB", 16f);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.BLACK);
        bubble.setBackground(rounded(
                Color.rgb(0, 255, 190),
                Color.rgb(170, 255, 235),
                100
        ));
        FrameLayout.LayoutParams bubbleParams =
                new FrameLayout.LayoutParams(dp(56), dp(56));
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.leftMargin = dp(16);
        bubbleParams.topMargin = dp(100);
        root.addView(bubble, bubbleParams);
        bubble.setOnTouchListener(this::onBubbleTouch);

        authoringBar = new LinearLayout(this);
        authoringBar.setOrientation(LinearLayout.VERTICAL);
        authoringBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        authoringBar.setBackground(rounded(
                Color.rgb(12, 20, 28),
                Color.rgb(90, 170, 255),
                12
        ));

        LinearLayout sectionRow = new LinearLayout(this);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        addAuthoringSection(sectionRow, "UI", AuthoringSection.UI);
        addAuthoringSection(sectionRow, "Logic", AuthoringSection.LOGIC);
        addAuthoringSection(sectionRow, "Data", AuthoringSection.DATA);
        addAuthoringSection(sectionRow, "Binding", AuthoringSection.BINDING);
        addAuthoringSection(sectionRow, "Asset", AuthoringSection.ASSET);
        authoringBar.addView(sectionRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView search = label("Cari Tombol", 13f);
        search.setOnClickListener(v -> renderAuthoringSearch("Tombol"));
        actionRow.addView(search);
        TextView template = label("Cari Template", 13f);
        template.setOnClickListener(v -> renderAuthoringSearch("template.screen.basic"));
        actionRow.addView(template);
        authoringBar.addView(actionRow);

        authoringStatus = label("Authoring: UI • siap", 13f);
        authoringStatus.setTextColor(Color.rgb(180, 220, 255));
        authoringBar.addView(authoringStatus);

        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView scan = label("Capability Scan", 12f);
        scan.setOnClickListener(v -> scanCapabilities());
        liveRow.addView(scan);
        TextView live = label("Live", 12f);
        live.setOnClickListener(v -> openLiveSession());
        liveRow.addView(live);
        TextView selfEdit = label("Self Edit", 12f);
        selfEdit.setOnClickListener(v -> queueSelfEdit());
        liveRow.addView(selfEdit);
        TextView apply = label("TERAPKAN", 12f);
        apply.setOnClickListener(v -> applyLiveSession());
        liveRow.addView(apply);
        authoringBar.addView(liveRow);

        liveStatus = label("Live: belum dipindai", 12f);
        liveStatus.setTextColor(Color.rgb(255, 190, 245));
        authoringBar.addView(liveStatus);

        LinearLayout integrationRow = new LinearLayout(this);
        integrationRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView importDemo = label("Import Demo", 13f);
        importDemo.setOnClickListener(v -> runExternalImport());
        integrationRow.addView(importDemo);
        TextView exportDemo = label("Export", 13f);
        exportDemo.setOnClickListener(v -> runExternalExport());
        integrationRow.addView(exportDemo);
        TextView syncDemo = label("Sync", 13f);
        syncDemo.setOnClickListener(v -> runExternalSync());
        integrationRow.addView(syncDemo);
        authoringBar.addView(integrationRow);

        integrationStatus = label("Eksternal: Sumber Demo • siap", 13f);
        integrationStatus.setTextColor(Color.rgb(170, 255, 220));
        authoringBar.addView(integrationStatus);

        LinearLayout repairRow = new LinearLayout(this);
        repairRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView repairDemo = label("Repair Demo", 13f);
        repairDemo.setOnClickListener(v -> runRepairDemo());
        repairRow.addView(repairDemo);
        TextView health = label("Health", 13f);
        health.setOnClickListener(v -> showHealth());
        repairRow.addView(health);
        TextView recovery = label("Recovery Preview", 13f);
        recovery.setOnClickListener(v -> showRecoveryPreview());
        repairRow.addView(recovery);
        authoringBar.addView(repairRow);

        repairStatus = label("Repair: siap • Health: HEALTHY", 13f);
        repairStatus.setTextColor(Color.rgb(255, 220, 150));
        authoringBar.addView(repairStatus);

        FrameLayout.LayoutParams authoringParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        authoringParams.gravity = Gravity.BOTTOM;
        authoringParams.leftMargin = dp(8);
        authoringParams.rightMargin = dp(8);
        authoringParams.bottomMargin = dp(8);
        root.addView(authoringBar, authoringParams);

        setContentView(root);
        root.post(this::restoreBubblePosition);
    }

    private boolean onBubbleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = view.getX();
                startY = view.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                    dragging = true;
                }
                float maxX = Math.max(0, root.getWidth() - view.getWidth());
                float maxY = Math.max(0, root.getHeight() - view.getHeight());
                view.setX(Math.max(0, Math.min(startX + dx, maxX)));
                view.setY(Math.max(0, Math.min(startY + dy, maxY)));
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    persistBubblePosition(view);
                } else {
                    boolean open = kernel.editorEnvironment()
                            .shell()
                            .bubbleController()
                            .tap();
                    edgeScroll.setVisibility(open ? View.VISIBLE : View.GONE);
                    if (open) {
                        edgeScroll.scrollTo(0, 0);
                        renderEdge();
                    }
                }
                return true;
            default:
                return true;
        }
    }

    private void renderEdge() {
        EdgePanelModel model = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        edgePanel.removeAllViews();

        TextView title = label(
                "‹ " + model.breadcrumb() + "\n" + model.titleIndonesia(),
                16f
        );
        title.setTextColor(Color.rgb(0, 255, 190));
        edgePanel.addView(title);

        TextView mode = label(
                kernel.editorEnvironment().shell().editEnabled()
                        ? "Edit ON • UI"
                        : "Edit OFF",
                13f
        );
        mode.setOnClickListener(v -> {
            boolean next = !kernel.editorEnvironment().shell().editEnabled();
            kernel.editorEnvironment().shell().setEditEnabled(next);
            renderEdge();
        });
        edgePanel.addView(mode);

        for (EdgeItem item : model.items()) {
            TextView row = label(item.labelIndonesia(), 14f);
            row.setEnabled(item.enabled());
            if ("edge.size".equals(item.itemId())) {
                row.setOnClickListener(v -> openFloatingSize());
            } else if ("edge.others".equals(item.itemId())) {
                row.setOnClickListener(v -> {
                    kernel.editorEnvironment()
                            .shell()
                            .activateFunction(EditorFunction.UI);
                    kernel.editorEnvironment().shell().clearSelection();
                    renderEdge();
                });
            }
            edgePanel.addView(row);
        }
    }

    private void openFloatingSize() {
        int[] location = new int[2];
        canvasObject.getLocationOnScreen(location);
        kernel.editorEnvironment().floatingEditor().open(
                "floating.size",
                "object.home.primary",
                new com.toolbox.tools.editor.EditorRect(
                        0,
                        0,
                        root.getWidth(),
                        root.getHeight()
                ),
                new com.toolbox.tools.editor.EditorRect(
                        location[0],
                        location[1],
                        location[0] + canvasObject.getWidth(),
                        location[1] + canvasObject.getHeight()
                ),
                dp(220),
                dp(150)
        );
        com.toolbox.tools.editor.EditorPoint point = kernel
                .editorEnvironment()
                .floatingEditor()
                .active()
                .position();
        floatingEditor.setX(point.x());
        floatingEditor.setY(point.y());
        floatingEditor.setVisibility(View.VISIBLE);
    }

    private void addAuthoringSection(
            LinearLayout row,
            String labelIndonesia,
            AuthoringSection section
    ) {
        TextView item = label(labelIndonesia, 13f);
        item.setOnClickListener(v -> {
            kernel.authoringWorkspace().activate(section);
            authoringStatus.setText(
                    "Authoring: " + labelIndonesia + " • siap"
            );
            edgeScroll.setVisibility(View.GONE);
        });
        row.addView(item);
    }

    private void renderAuthoringSearch(String query) {
        java.util.List<AuthoringSearchResult> results =
                kernel.authoringWorkspace().searchAll(query, 12);
        StringBuilder text = new StringBuilder();
        text.append("Cari: ").append(query).append(" • ")
                .append(results.size()).append(" hasil");
        if (!results.isEmpty()) {
            AuthoringSearchResult first = results.get(0);
            text.append("\n")
                    .append(first.kind().name())
                    .append(" • ")
                    .append(first.stableId());
        }
        authoringStatus.setText(text.toString());
    }

    private void scanCapabilities() {
        CapabilityScanResult result = kernel.capabilityScanner()
                .scan(kernel.selfTargetDescriptor());
        liveStatus.setText(
                "Capability: UI "
                        + result.status(CapabilityArea.UI).name()
                        + " • Runtime "
                        + result.status(CapabilityArea.RUNTIME).name()
        );
    }

    private void openLiveSession() {
        try {
            if (kernel.projectManager().hasUnsavedChanges()
                    || kernel.projectManager().savedRevision() <= 0) {
                kernel.projectManager().save();
            }
            CapabilityScanResult scan = kernel.capabilityScanner()
                    .scan(kernel.selfTargetDescriptor());
            kernel.liveSessionManager().open(
                    "live.toolbox.self",
                    kernel.selfTargetDescriptor(),
                    scan
            );
            kernel.editorEnvironment()
                    .shell()
                    .setLiveCapability(scan.liveAvailable());
            kernel.editorEnvironment()
                    .shell()
                    .setMode(com.toolbox.tools.editor.EditorMode.LIVE);
            liveStatus.setText("Live: OPEN • ToolBox Sendiri");
        } catch (Exception error) {
            liveStatus.setText("Live: GAGAL AMAN");
        }
    }

    private void queueSelfEdit() {
        try {
            if (kernel.liveSessionManager().state()
                    == LiveSessionState.CLOSED) {
                openLiveSession();
            }
            if (kernel.liveSessionManager().state()
                    != LiveSessionState.OPEN
                    && kernel.liveSessionManager().state()
                    != LiveSessionState.APPLIED
                    && kernel.liveSessionManager().state()
                    != LiveSessionState.DIRTY) {
                liveStatus.setText("Self Edit: LIVE TIDAK SIAP");
                return;
            }
            kernel.liveSessionManager().queue(
                    new LiveChange(
                            "change.self.screen",
                            "screen.self.live",
                            LiveChangeOperation.UPSERT,
                            "Tahap 9 Self Edit"
                    )
            );
            liveStatus.setText(
                    "Live: DIRTY • "
                            + kernel.liveSessionManager()
                            .queuedChangeCount()
                            + " perubahan"
            );
        } catch (Exception error) {
            liveStatus.setText("Self Edit: DILINDUNGI/GAGAL");
        }
    }

    private void applyLiveSession() {
        try {
            LiveApplyResult result =
                    kernel.liveSessionManager().terapkan();
            if (result.isPass()
                    && result.state() == LiveSessionState.APPLIED) {
                liveStatus.setText(
                        "TERAPKAN: PASS • "
                                + kernel.repairSessionManager()
                                .phase()
                                .name()
                );
            } else {
                liveStatus.setText(
                        "TERAPKAN: "
                                + result.state().name()
                                + " • "
                                + result.message()
                );
            }
        } catch (Exception error) {
            liveStatus.setText("TERAPKAN: GAGAL AMAN");
        }
    }

    private void runExternalImport() {
        ExternalSnapshot snapshot = kernel.externalIntegrationManager()
                .demoSnapshot(1, "cursor.ui.1");
        NormalizationResult normalized = kernel.externalIntegrationManager()
                .importSnapshot(snapshot);
        integrationStatus.setText(
                normalized.isPass()
                        ? "Import: PASS • " + normalized.records().size() + " record"
                        : "Import: GAGAL"
        );
    }

    private void runExternalExport() {
        ExternalSnapshot snapshot = kernel.externalIntegrationManager()
                .demoSnapshot(1, "cursor.ui.export");
        NormalizationResult normalized = kernel.externalIntegrationManager()
                .importSnapshot(snapshot);
        ExportPackage exported = kernel.externalIntegrationManager()
                .export(normalized.records());
        integrationStatus.setText(
                "Export: PASS • SHA256 "
                        + exported.sha256().substring(0, 12)
        );
    }

    private void runExternalSync() {
        ExternalSnapshot snapshot = kernel.externalIntegrationManager()
                .demoSnapshot(2, "cursor.ui.sync.2");
        SyncPlan plan = kernel.externalIntegrationManager().planSync(snapshot);
        if (plan.status() == com.toolbox.tools.integration.SyncStatus.CLEAN) {
            kernel.externalIntegrationManager().applySync(plan);
        }
        integrationStatus.setText(
                "Sync: " + plan.status().name()
                        + " • " + kernel.externalIntegrationManager().sync().cursor()
        );
    }

    private void runRepairDemo() {
        try {
            if (kernel.projectManager().savedRevision() <= 0) {
                kernel.projectManager().save();
            }

            long baseRevision = kernel.projectManager().savedRevision();
            RepairPlan plan = new RepairPlan(
                    "repair.demo." + baseRevision,
                    kernel.projectManager().current().projectId(),
                    baseRevision,
                    java.util.Collections.singletonMap(
                            "screen.repair.demo",
                            "Tahap 8 Repair Demo"
                    ),
                    java.util.Collections.emptySet()
            );
            RepairValidationResult staged =
                    kernel.repairSessionManager().stage(plan);
            if (!staged.isPass()) {
                repairStatus.setText("Repair: STAGING GAGAL");
                return;
            }
            kernel.repairSessionManager().activate();
            boolean verified =
                    kernel.repairSessionManager().verifyOrRollback();
            repairStatus.setText(
                    verified
                            ? "Repair: VERIFIED • Recovery Point: PASS"
                            : "Repair: ROLLED_BACK"
            );
        } catch (Exception error) {
            repairStatus.setText("Repair: GAGAL AMAN");
        }
    }

    private void showHealth() {
        HealthReport report = kernel.healthMonitor().inspect(kernel);
        repairStatus.setText(
                "Health: " + report.state().name()
                        + " • alasan " + report.reasons().size()
        );
    }

    private void showRecoveryPreview() {
        try {
            int count = kernel.recoveryPreviewService()
                    .candidates()
                    .size();
            repairStatus.setText(
                    "Recovery Preview: " + count
                            + " kandidat • pilih manual"
            );
        } catch (Exception error) {
            repairStatus.setText("Recovery Preview: GAGAL AMAN");
        }
    }

    private void persistBubblePosition(View view) {
        String suffix = orientationSuffix();
        getPreferences(MODE_PRIVATE)
                .edit()
                .putFloat("bubble.x." + suffix, view.getX())
                .putFloat("bubble.y." + suffix, view.getY())
                .apply();
    }

    private void restoreBubblePosition() {
        String suffix = orientationSuffix();
        float x = getPreferences(MODE_PRIVATE)
                .getFloat("bubble.x." + suffix, dp(16));
        float y = getPreferences(MODE_PRIVATE)
                .getFloat("bubble.y." + suffix, dp(100));
        float maxX = Math.max(0, root.getWidth() - bubble.getWidth());
        float maxY = Math.max(0, root.getHeight() - bubble.getHeight());
        bubble.setX(Math.max(0, Math.min(x, maxX)));
        bubble.setY(Math.max(0, Math.min(y, maxY)));
    }

    private String orientationSuffix() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE
                ? "landscape"
                : "portrait";
    }

    private TextView label(String text, float sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(220, 230, 240));
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        return view;
    }

    private GradientDrawable rounded(
            int fill,
            int stroke,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private FrameLayout.LayoutParams frameMatch() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
