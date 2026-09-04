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
                "ToolBox Tahap 6\nUI • Logic • Data • Binding • Asset\nAuthoring • Search • Template\n" + status,
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
