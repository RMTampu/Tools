package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

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
    }

    private void build() {
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
        primaryButton.setEnabled(boolResource(
                "ui.object.home.primary.enabled",
                true
        ));
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

    private boolean handlePrimaryTouch(View view, MotionEvent event) {
        if (!kernel.editorEnvironment().shell().selectionAvailable()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
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
            Toast.makeText(
                    getContext(),
                    "Aksi aplikasi dijalankan: membuka layar Detail.",
                    Toast.LENGTH_SHORT
            ).show();
        }
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
