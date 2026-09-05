package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.editor.VisualCapability;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.editor.VisualEditOperation;
import com.toolbox.tools.editor.VisualEditTransaction;
import com.toolbox.tools.product.InputRouter;
import com.toolbox.tools.product.VisualLayoutEngine;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiCanvasView extends FrameLayout {
    public interface SelectionListener {
        void onSelected(String objectId);
    }

    private final AppKernel kernel;
    private final SelectionListener listener;
    private TextView primaryButton;
    private float downX;
    private float downY;
    private float startX;
    private float startY;
    private boolean dragging;
    private FrameLayout viewportContent;
    private ScaleGestureDetector scaleDetector;
    private float viewportScale = 1f;
    private float viewportPanX;
    private float viewportPanY;
    private float lastMultiFocusX;
    private float lastMultiFocusY;
    private boolean viewportGesture;

    public UiCanvasView(
            Context context,
            AppKernel kernel,
            SelectionListener listener
    ) {
        super(context);
        this.kernel = kernel;
        this.listener = listener;
        setClipChildren(false);
        setBackground(UiKit.kartuPx(
                context,
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                28,
                1
        ));
        build();
        installViewportGestures();
    }

    private void installViewportGestures() {
        if (getChildCount() == 0) return;

        viewportContent = new FrameLayout(getContext());
        viewportContent.setClipChildren(false);
        viewportContent.setClipToPadding(false);

        java.util.List<View> original = new java.util.ArrayList<>();
        java.util.List<LayoutParams> params = new java.util.ArrayList<>();
        while (getChildCount() > 0) {
            View child = getChildAt(0);
            LayoutParams layoutParams =
                    (LayoutParams) child.getLayoutParams();
            removeViewAt(0);
            original.add(child);
            params.add(layoutParams);
        }
        for (int i = 0; i < original.size(); i++) {
            viewportContent.addView(original.get(i), params.get(i));
        }
        addView(
                viewportContent,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );

        VisualLayoutEngine layout =
                kernel.productServices().visualLayout();
        viewportScale = clamp(
                layout.zoom(),
                0.5f,
                3.0f
        );
        viewportPanX = layout.panX();
        viewportPanY = layout.panY();
        applyViewportTransform();

        scaleDetector = new ScaleGestureDetector(
                getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector
                    ) {
                        float before = viewportScale;
                        float after = clamp(
                                before * detector.getScaleFactor(),
                                0.5f,
                                3.0f
                        );
                        if (Math.abs(after - before) < 0.001f) {
                            return true;
                        }

                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        float designX =
                                (focusX - viewportPanX) / before;
                        float designY =
                                (focusY - viewportPanY) / before;

                        viewportScale = after;
                        viewportPanX =
                                focusX - designX * viewportScale;
                        viewportPanY =
                                focusY - designY * viewportScale;
                        clampViewportPan();
                        applyViewportTransform();
                        return true;
                    }
                }
        );
        setContentDescription(
                "Canvas UI • pinch untuk zoom • dua jari untuk pan"
        );
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getPointerCount() >= 2
                || viewportGesture) {
            viewportGesture = true;
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleDetector != null) {
            scaleDetector.onTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (event.getPointerCount() >= 2) {
            float focusX = averageX(event);
            float focusY = averageY(event);
            if (action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_DOWN
                    || lastMultiFocusX == 0f && lastMultiFocusY == 0f) {
                lastMultiFocusX = focusX;
                lastMultiFocusY = focusY;
            } else if (action == MotionEvent.ACTION_MOVE
                    && (scaleDetector == null
                        || !scaleDetector.isInProgress())) {
                viewportPanX += focusX - lastMultiFocusX;
                viewportPanY += focusY - lastMultiFocusY;
                lastMultiFocusX = focusX;
                lastMultiFocusY = focusY;
                clampViewportPan();
                applyViewportTransform();
            }
            viewportGesture = true;
            return true;
        }

        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP) {
            lastMultiFocusX = 0f;
            lastMultiFocusY = 0f;
            viewportGesture = false;
        }
        return viewportGesture || super.onTouchEvent(event);
    }

    private void applyViewportTransform() {
        if (viewportContent == null) return;
        viewportContent.setPivotX(0f);
        viewportContent.setPivotY(0f);
        viewportContent.setScaleX(viewportScale);
        viewportContent.setScaleY(viewportScale);
        viewportContent.setTranslationX(viewportPanX);
        viewportContent.setTranslationY(viewportPanY);
        kernel.productServices().visualLayout().setViewport(
                viewportScale,
                viewportPanX,
                viewportPanY
        );
    }

    private void clampViewportPan() {
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());
        float scaledWidth = width * viewportScale;
        float scaledHeight = height * viewportScale;

        float minX = Math.min(
                0f,
                width - scaledWidth
        );
        float minY = Math.min(
                0f,
                height - scaledHeight
        );
        viewportPanX = clamp(
                viewportPanX,
                minX - width * 0.25f,
                width * 0.25f
        );
        viewportPanY = clamp(
                viewportPanY,
                minY - height * 0.25f,
                height * 0.25f
        );
    }

    private static float averageX(MotionEvent event) {
        float total = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            total += event.getX(i);
        }
        return total / Math.max(1, event.getPointerCount());
    }

    private static float averageY(MotionEvent event) {
        float total = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            total += event.getY(i);
        }
        return total / Math.max(1, event.getPointerCount());
    }

    private static float clamp(
            float value,
            float min,
            float max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    private void build() {
        if ("screen.detail".equals(
                kernel.runtimeEnvironment().navigation().current().screenId()
        )) {
            buildDetail();
            return;
        }
        Context c = getContext();

        LinearLayout status = UiKit.baris(c);
        status.setPadding(
                UiKit.dp(c, 14),
                UiKit.dp(c, 8),
                UiKit.dp(c, 14),
                UiKit.dp(c, 8)
        );
        TextView waktu = UiKit.teks(c, "21:48", 10f, UiKit.TEKS_REDUP);
        status.addView(waktu, new LinearLayout.LayoutParams(0, UiKit.dp(c, 24), 1));
        TextView signal = UiKit.teks(c, "●  ●  82%", 10f, UiKit.TEKS_REDUP);
        signal.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.addView(signal, new LinearLayout.LayoutParams(0, UiKit.dp(c, 24), 1));
        addView(status, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 42),
                Gravity.TOP
        ));

        LinearLayout screen = UiKit.kolom(c);
        screen.setPadding(
                UiKit.dp(c, 18),
                UiKit.dp(c, 18),
                UiKit.dp(c, 18),
                UiKit.dp(c, 18)
        );
        FrameLayout.LayoutParams screenParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        screenParams.topMargin = UiKit.dp(c, 42);
        addView(screen, screenParams);

        TextView eyebrow = UiKit.teks(
                c,
                "BERANDA  •  PRATINJAU LANGSUNG",
                10f,
                UiKit.NEON
        );
        screen.addView(eyebrow);

        TextView title = UiKit.judul(
                c,
                resource("ui.screen.home.title", "Bangun aplikasi secara visual"),
                22f
        );
        title.setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 4));
        screen.addView(title);

        TextView sub = UiKit.teks(
                c,
                resource(
                        "ui.screen.home.subtitle",
                        "Layar ini adalah permukaan yang sama saat Edit aktif maupun nonaktif."
                ),
                12f,
                UiKit.TEKS_REDUP
        );
        screen.addView(sub);

        UiKit.ruang(screen, c, 18);

        LinearLayout card = UiKit.kolom(c);
        card.setPadding(
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 14),
                UiKit.dp(c, 14)
        );
        card.setBackground(UiKit.kartuPx(
                c,
                UiKit.PERMUKAAN_2,
                UiKit.GARIS,
                18,
                1
        ));

        TextView cardTitle = UiKit.judul(c, "Komponen interaktif", 15f);
        card.addView(cardTitle);

        TextView cardSub = UiKit.teks(
                c,
                "Pilih objek saat Edit aktif. Saat Edit nonaktif, objek menjalankan aksi aplikasi.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        cardSub.setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 10));
        card.addView(cardSub);

        EditText input = new EditText(c);
        input.setHint("Masukkan nama");
        input.setHintTextColor(UiKit.TEKS_REDUP);
        input.setTextColor(UiKit.TEKS);
        input.setTextSize(13f);
        input.setSingleLine(true);
        input.setBackground(UiKit.kartuPx(
                c,
                UiKit.LATAR,
                UiKit.GARIS,
                12,
                1
        ));
        input.setPadding(
                UiKit.dp(c, 12),
                UiKit.dp(c, 8),
                UiKit.dp(c, 12),
                UiKit.dp(c, 8)
        );
        card.addView(input, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 48)
        ));

        UiKit.ruang(card, c, 10);

        FrameLayout freeArea = new FrameLayout(c);
        freeArea.setBackground(UiKit.kartuPx(
                c,
                Color.rgb(9, 22, 29),
                Color.rgb(23, 63, 68),
                14,
                1
        ));
        card.addView(freeArea, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 122)
        ));
        configureDropTarget(freeArea);

        TextView guide = UiKit.teks(
                c,
                "Area tata letak responsif • seret objek saat Edit aktif",
                10f,
                UiKit.TEKS_REDUP
        );
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                UiKit.dp(c, 28),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        guideParams.topMargin = UiKit.dp(c, 8);
        freeArea.addView(guide, guideParams);

        primaryButton = UiKit.judul(
                c,
                resource("ui.object.home.primary.text", "Buka Detail"),
                floatResource("ui.object.home.primary.text.size.sp", 13f)
        );
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setTextColor(buttonTextColor());
        int radius = intResource("ui.object.home.primary.radius.dp", 14);
        int border = intResource("ui.object.home.primary.border.dp", 1);
        int fill = buttonColor();
        primaryButton.setBackground(UiKit.kartuPx(
                c,
                fill,
                fill == UiKit.NEON ? UiKit.NEON_BIRU : UiKit.NEON,
                radius,
                border
        ));

        int padding = intResource("ui.object.home.primary.padding.dp", 12);
        primaryButton.setPadding(
                UiKit.dp(c, padding),
                UiKit.dp(c, 6),
                UiKit.dp(c, padding),
                UiKit.dp(c, 6)
        );
        primaryButton.setAlpha(clamp(floatResource(
                "ui.object.home.primary.opacity",
                1f
        ), 0f, 1f));
        primaryButton.setRotation(floatResource(
                "ui.object.home.primary.rotation",
                0f
        ));
        float scale = clamp(floatResource(
                "ui.object.home.primary.scale",
                1f
        ), 0.2f, 3f);
        primaryButton.setScaleX(scale);
        primaryButton.setScaleY(scale);
        primaryButton.setElevation(UiKit.dp(
                c,
                intResource("ui.object.home.primary.elevation.dp", 4)
        ));
        Map<String, String> conditionContext = new LinkedHashMap<>();
        conditionContext.put(
                "data.valid",
                resource("data.valid", "true")
        );
        conditionContext.put(
                "user.role",
                resource("user.role", "admin")
        );
        boolean conditionVisible = kernel.productServices()
                .conditionalProperties()
                .evaluate(
                        resource(
                                "ui.object.home.primary.visible.if",
                                "true"
                        ),
                        conditionContext
                );
        boolean conditionEnabled = kernel.productServices()
                .conditionalProperties()
                .evaluate(
                        resource(
                                "ui.object.home.primary.enabled.if",
                                "true"
                        ),
                        conditionContext
                );
        primaryButton.setVisibility(
                conditionVisible ? View.VISIBLE : View.GONE
        );
        primaryButton.setEnabled(
                boolResource(
                        "ui.object.home.primary.enabled",
                        true
                ) && conditionEnabled
        );
        primaryButton.setContentDescription(resource(
                "ui.object.home.primary.accessibility.label",
                resource("ui.object.home.primary.text", "Buka Detail")
        ));

        int buttonWidth = intResource(
                "ui.object.home.primary.width.dp",
                148
        );
        int buttonHeight = intResource(
                "ui.object.home.primary.height.dp",
                46
        );
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                UiKit.dp(c, buttonWidth),
                UiKit.dp(c, buttonHeight)
        );
        buttonParams.leftMargin = UiKit.dp(
                c,
                intResource("ui.object.home.primary.position.x.dp", 18)
        );
        buttonParams.topMargin = UiKit.dp(
                c,
                intResource("ui.object.home.primary.position.y.dp", 50)
        );
        freeArea.addView(primaryButton, buttonParams);
        renderDroppedObjects(freeArea);

        primaryButton.setOnTouchListener(this::handlePrimaryTouch);
        primaryButton.setOnClickListener(v -> {
            if (dragging) return;
            if (kernel.editorEnvironment().shell().selectionAvailable()) {
                selectPrimary();
            } else {
                runPrimaryAction();
            }
        });

        playConfiguredAnimation();

        UiKit.ruang(card, c, 12);

        LinearLayout progressRow = UiKit.baris(c);
        TextView progressLabel = UiKit.teks(
                c,
                "Kesiapan layar",
                11.5f,
                UiKit.TEKS_REDUP
        );
        progressRow.addView(progressLabel, new LinearLayout.LayoutParams(
                0,
                UiKit.dp(c, 28),
                1
        ));
        TextView progressValue = UiKit.judul(c, "100%", 11.5f);
        progressValue.setTextColor(UiKit.NEON);
        progressValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        progressRow.addView(progressValue, new LinearLayout.LayoutParams(
                0,
                UiKit.dp(c, 28),
                1
        ));
        card.addView(progressRow);

        ProgressBar progress = new ProgressBar(
                c,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progress.setMax(100);
        progress.setProgress(100);
        progress.getProgressDrawable().setTint(UiKit.NEON);
        card.addView(progress, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                UiKit.dp(c, 5)
        ));

        screen.addView(card, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));

        UiKit.ruang(screen, c, 14);
        TextView mode = UiKit.teks(
                c,
                kernel.editorEnvironment().shell().selectionAvailable()
                        ? "Edit aktif • sentuh atau seret objek untuk mengubah."
                        : "Interaksi aplikasi aktif • tombol menjalankan aksi.",
                11f,
                UiKit.TEKS_REDUP
        );
        screen.addView(mode);
    }

    private void configureDropTarget(FrameLayout freeArea) {
        freeArea.setOnDragListener((view, event) -> {
            if (!kernel.editorEnvironment().shell().selectionAvailable()) {
                return false;
            }
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription() != null;
                case DragEvent.ACTION_DRAG_ENTERED:
                    freeArea.setAlpha(0.88f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    freeArea.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    freeArea.setAlpha(1f);
                    if (event.getClipData() == null
                            || event.getClipData().getItemCount() == 0) {
                        return false;
                    }
                    CharSequence text = event.getClipData()
                            .getItemAt(0)
                            .coerceToText(getContext());
                    if (text == null) return false;
                    createDroppedObject(
                            freeArea,
                            text.toString(),
                            event.getX(),
                            event.getY()
                    );
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    freeArea.setAlpha(1f);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void createDroppedObject(
            FrameLayout freeArea,
            String payload,
            float x,
            float y
    ) {
        String kind;
        String label;
        String assetId = null;
        if (payload.startsWith("component.")) {
            kind = payload;
            label = "Komponen Baru";
        } else if (payload.startsWith("asset.")) {
            kind = "asset";
            assetId = payload;
            label = resource(
                    payload + ".name",
                    payload.startsWith("asset.external.")
                            ? "Aset Eksternal"
                            : "Aset Baru"
            );
        } else {
            Toast.makeText(
                    getContext(),
                    "Payload drag tidak dikenali.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String objectId = "ui.object.drop."
                + Long.toHexString(System.nanoTime());
        LinkedHashMap<String, String> updates = new LinkedHashMap<>();
        updates.put(objectId + ".kind", kind);
        updates.put(objectId + ".text", label);
        if (assetId != null) {
            updates.put(objectId + ".asset.id", assetId);
        }
        updates.put(
                objectId + ".position.x.dp",
                String.valueOf(Math.max(
                        0,
                        Math.round(
                                x / getResources().getDisplayMetrics().density
                        )
                ))
        );
        updates.put(
                objectId + ".position.y.dp",
                String.valueOf(Math.max(
                        28,
                        Math.round(
                                y / getResources().getDisplayMetrics().density
                        )
                ))
        );
        updates.put(
                objectId + ".width.dp",
                assetId == null ? "112" : "160"
        );
        updates.put(
                objectId + ".height.dp",
                assetId == null ? "40" : "110"
        );
        try {
            kernel.projectManager().applyResourceTransaction(
                    updates,
                    Collections.emptySet()
            );
            if (!kernel.productServices()
                    .visualLayout()
                    .snapshot()
                    .containsKey(objectId)) {
                kernel.productServices().visualLayout().add(
                        new VisualLayoutEngine.Node(
                                objectId,
                                "layout.root",
                                Math.max(0, x),
                                Math.max(0, y),
                                UiKit.dp(getContext(), 112),
                                UiKit.dp(getContext(), 40),
                                2,
                                false,
                                VisualLayoutEngine.PointerBehavior.AUTO
                        )
                );
                kernel.productServices().inputRouter().register(
                        objectId,
                        "container.home.main"
                );
            }
            renderDroppedObject(freeArea, objectId);
            Toast.makeText(
                    getContext(),
                    "Objek ditambahkan • " + objectId,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException error) {
            Toast.makeText(
                    getContext(),
                    "Objek ditolak secara aman.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void renderDroppedObjects(FrameLayout freeArea) {
        for (Map.Entry<String, String> entry
                : kernel.projectManager().current().resources().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("ui.object.drop.")
                    || !key.endsWith(".text")) {
                continue;
            }
            String objectId = key.substring(
                    0,
                    key.length() - ".text".length()
            );
            renderDroppedObject(freeArea, objectId);
        }
    }

    private void renderDroppedObject(
            FrameLayout freeArea,
            String objectId
    ) {
        if (freeArea.findViewWithTag(objectId) != null) {
            return;
        }
        if (!kernel.productServices().visualLayout().snapshot().containsKey(objectId)) {
            kernel.productServices().visualLayout().add(
                    new VisualLayoutEngine.Node(
                            objectId,
                            "layout.root",
                            UiKit.dp(
                                    getContext(),
                                    intResource(objectId + ".position.x.dp", 22)
                            ),
                            UiKit.dp(
                                    getContext(),
                                    intResource(objectId + ".position.y.dp", 70)
                            ),
                            UiKit.dp(
                                    getContext(),
                                    intResource(objectId + ".width.dp", 112)
                            ),
                            UiKit.dp(
                                    getContext(),
                                    intResource(objectId + ".height.dp", 40)
                            ),
                            2,
                            false,
                            VisualLayoutEngine.PointerBehavior.AUTO
                    )
            );
            try {
                kernel.productServices().inputRouter().register(
                        objectId,
                        "container.home.main"
                );
            } catch (RuntimeException ignored) {
                // Sudah terdaftar.
            }
        }
        View object;
        String assetId = resource(
                objectId + ".asset.id",
                ""
        );
        if (assetId.startsWith("asset.external.")) {
            try {
                object = AndroidAssetRenderer.render(
                        getContext(),
                        kernel,
                        assetId,
                        UiKit.dp(
                                getContext(),
                                intResource(
                                        objectId + ".width.dp",
                                        160
                                )
                        ),
                        UiKit.dp(
                                getContext(),
                                intResource(
                                        objectId + ".height.dp",
                                        110
                                )
                        )
                );
            } catch (Exception error) {
                TextView failed = UiKit.teks(
                        getContext(),
                        "Aset tidak dapat dimuat\nIntegrity/format gagal",
                        10f,
                        UiKit.BAHAYA
                );
                failed.setGravity(Gravity.CENTER);
                object = failed;
            }
        } else {
            TextView text = UiKit.judul(
                    getContext(),
                    resource(objectId + ".text", "Objek"),
                    11f
            );
            text.setGravity(Gravity.CENTER);
            text.setTextColor(UiKit.TEKS);
            text.setBackground(UiKit.kartuPx(
                    getContext(),
                    UiKit.PERMUKAAN_2,
                    UiKit.NEON_BIRU,
                    12,
                    1
            ));
            object = text;
        }
        object.setTag(objectId);
        int width = intResource(
                objectId + ".width.dp",
                assetId.startsWith("asset.") ? 160 : 112
        );
        int height = intResource(
                objectId + ".height.dp",
                assetId.startsWith("asset.") ? 110 : 40
        );
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                UiKit.dp(getContext(), width),
                UiKit.dp(getContext(), height)
        );
        params.leftMargin = UiKit.dp(
                getContext(),
                intResource(objectId + ".position.x.dp", 22)
        );
        params.topMargin = UiKit.dp(
                getContext(),
                intResource(objectId + ".position.y.dp", 70)
        );
        freeArea.addView(object, params);
        object.setOnTouchListener(
                (view, event) -> handleDroppedTouch(
                        view,
                        event,
                        objectId
                )
        );
    }

    private boolean handleDroppedTouch(
            View view,
            MotionEvent event,
            String objectId
    ) {
        if (!kernel.editorEnvironment().shell().selectionAvailable()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                try {
                    kernel.productServices().inputRouter().dispatch(
                            objectId,
                            InputRouter.Event.TAP,
                            InputRouter.Propagation.CONTINUE
                    );
                } catch (RuntimeException ignored) {
                    // Objek lama akan diregistrasi pada render berikutnya.
                }
                downX = event.getRawX();
                downY = event.getRawY();
                startX = view.getX();
                startY = view.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (Math.abs(dx) > UiKit.dp(getContext(), 4)
                        || Math.abs(dy) > UiKit.dp(getContext(), 4)) {
                    dragging = true;
                }
                view.setX(Math.max(0, startX + dx));
                view.setY(Math.max(0, startY + dy));
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    LinkedHashMap<String, String> updates =
                            new LinkedHashMap<>();
                    updates.put(
                            objectId + ".position.x.dp",
                            String.valueOf(Math.round(
                                    view.getX()
                                            / getResources()
                                            .getDisplayMetrics().density
                            ))
                    );
                    updates.put(
                            objectId + ".position.y.dp",
                            String.valueOf(Math.round(
                                    view.getY()
                                            / getResources()
                                            .getDisplayMetrics().density
                            ))
                    );
                    try {
                        kernel.projectManager().applyResourceTransaction(
                                updates,
                                Collections.emptySet()
                        );
                    } catch (RuntimeException error) {
                        Toast.makeText(
                                getContext(),
                                "Posisi objek gagal disimpan.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                } else {
                    try {
                        kernel.editorEnvironment().shell().selectObject(
                                objectId
                        );
                        if (listener != null) {
                            listener.onSelected(objectId);
                        }
                    } catch (RuntimeException error) {
                        Toast.makeText(
                                getContext(),
                                "Objek tidak tersedia untuk dipilih.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private boolean handlePrimaryTouch(View view, MotionEvent event) {
        if (!kernel.editorEnvironment().shell().selectionAvailable()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                kernel.productServices().inputRouter().dispatch(
                        "object.home.primary",
                        InputRouter.Event.TAP,
                        InputRouter.Propagation.CONTINUE
                );
                downX = event.getRawX();
                downY = event.getRawY();
                startX = view.getX();
                startY = view.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (Math.abs(dx) > UiKit.dp(getContext(), 4)
                        || Math.abs(dy) > UiKit.dp(getContext(), 4)) {
                    dragging = true;
                }
                view.setX(Math.max(0, startX + dx));
                view.setY(Math.max(0, startY + dy));
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    commitPosition(view);
                } else {
                    selectPrimary();
                }
                return true;
            default:
                return false;
        }
    }

    private void selectPrimary() {
        try {
            kernel.editorEnvironment().visualSession().select(
                    "object.home.primary"
            );
            kernel.editorEnvironment().shell().selectObject(
                    "object.home.primary"
            );
            primaryButton.setBackground(UiKit.kartuPx(
                    getContext(),
                    buttonColor(),
                    UiKit.NEON_BIRU,
                    intResource("ui.object.home.primary.radius.dp", 14),
                    Math.max(2, intResource("ui.object.home.primary.border.dp", 1))
            ));
            if (listener != null) listener.onSelected("object.home.primary");
        } catch (RuntimeException error) {
            Toast.makeText(
                    getContext(),
                    "Objek tidak tersedia untuk diedit.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void commitPosition(View view) {
        try {
            VisualEditTransaction tx = new VisualEditTransaction(
                    "transaction.position." + System.nanoTime(),
                    Arrays.asList(
                            new VisualEditOperation(
                                    "object.home.primary",
                                    VisualCapability.POSITION,
                                    "property.position.x",
                                    String.valueOf(Math.round(view.getX()))
                            ),
                            new VisualEditOperation(
                                    "object.home.primary",
                                    VisualCapability.POSITION,
                                    "property.position.y",
                                    String.valueOf(Math.round(view.getY()))
                            )
                    )
            );
            kernel.editorEnvironment().visualSession().apply(
                    tx,
                    VisualCapabilitySet.defaultEditable()
            );

            LinkedHashMap<String, String> updates = new LinkedHashMap<>();
            updates.put(
                    "ui.object.home.primary.position.x.dp",
                    String.valueOf(Math.round(
                            view.getX() / getResources().getDisplayMetrics().density
                    ))
            );
            updates.put(
                    "ui.object.home.primary.position.y.dp",
                    String.valueOf(Math.round(
                            view.getY() / getResources().getDisplayMetrics().density
                    ))
            );
            kernel.projectManager().applyResourceTransaction(
                    updates,
                    Collections.emptySet()
            );
            kernel.productServices().visualLayout().move(
                    "object.home.primary",
                    view.getX(),
                    view.getY(),
                    UiKit.dp(getContext(), 8)
            );
            selectPrimary();
        } catch (RuntimeException error) {
            Toast.makeText(
                    getContext(),
                    "Posisi tidak dapat diubah karena objek terkunci.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void runPrimaryAction() {
        kernel.productServices().inputRouter().dispatch(
                "object.home.primary",
                InputRouter.Event.TAP,
                InputRouter.Propagation.CONTINUE
        );
        String action = resource(
                "logic.ui.home.primary.action",
                "open.detail"
        );
        if ("none".equals(action)) {
            Toast.makeText(
                    getContext(),
                    "Tidak ada aksi yang terhubung.",
                    Toast.LENGTH_SHORT
            ).show();
        } else if ("show.message".equals(action)) {
            Toast.makeText(
                    getContext(),
                    "Aksi aplikasi dijalankan.",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            LinkedHashMap<String, String> parameters =
                    new LinkedHashMap<>();
            parameters.put("parameter.item", "item.default");
            kernel.runtimeEnvironment()
                    .navigation()
                    .navigate("route.detail", parameters);
            removeAllViews();
            build();
        }
    }

    private void buildDetail() {
        Context c = getContext();
        LinearLayout root = UiKit.kolom(c);
        root.setPadding(
                UiKit.dp(c, 24),
                UiKit.dp(c, 48),
                UiKit.dp(c, 24),
                UiKit.dp(c, 24)
        );
        root.addView(UiKit.teks(
                c,
                "DETAIL • RUNTIME NAVIGATION",
                10f,
                UiKit.NEON
        ));
        root.addView(UiKit.judul(
                c,
                "Layar Detail",
                24f
        ));
        TextView detail = UiKit.teks(
                c,
                "Navigasi ini dijalankan oleh NavigationManager dan Back Stack produksi.",
                12f,
                UiKit.TEKS_REDUP
        );
        detail.setPadding(
                0,
                UiKit.dp(c, 8),
                0,
                UiKit.dp(c, 18)
        );
        root.addView(detail);

        TextView back = UiKit.tombol(
                c,
                "Kembali ke Beranda",
                true
        );
        back.setOnClickListener(v -> {
            kernel.runtimeEnvironment().navigation().back();
            removeAllViews();
            build();
        });
        root.addView(back);
        addView(root, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
    }

    private void playConfiguredAnimation() {
        String animation = resource(
                "ui.object.home.primary.animation",
                "none"
        );
        float targetAlpha = primaryButton.getAlpha();
        float targetScaleX = primaryButton.getScaleX();
        float targetScaleY = primaryButton.getScaleY();

        if ("fade".equals(animation)) {
            primaryButton.setAlpha(0.2f);
            primaryButton.animate()
                    .alpha(targetAlpha)
                    .setDuration(260)
                    .start();
        } else if ("scale".equals(animation)) {
            primaryButton.setScaleX(targetScaleX * 0.82f);
            primaryButton.setScaleY(targetScaleY * 0.82f);
            primaryButton.animate()
                    .scaleX(targetScaleX)
                    .scaleY(targetScaleY)
                    .setDuration(260)
                    .start();
        }
    }

    private int buttonColor() {
        String value = resource(
                "ui.object.home.primary.color",
                "neon"
        );
        if ("blue".equals(value)) return UiKit.NEON_BIRU;
        if ("surface".equals(value)) return UiKit.PERMUKAAN_2;
        return UiKit.NEON;
    }

    private int buttonTextColor() {
        return "surface".equals(resource(
                "ui.object.home.primary.color",
                "neon"
        )) ? UiKit.TEKS : UiKit.LATAR;
    }

    private String resource(String id, String fallback) {
        String value = kernel.projectManager().current().resources().get(id);
        return value == null ? fallback : value;
    }

    private int intResource(String id, int fallback) {
        try {
            return Integer.parseInt(resource(id, String.valueOf(fallback)));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private float floatResource(String id, float fallback) {
        try {
            return Float.parseFloat(resource(id, String.valueOf(fallback)));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private boolean boolResource(String id, boolean fallback) {
        String value = resource(id, String.valueOf(fallback));
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
