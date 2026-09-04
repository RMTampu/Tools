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

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.editor.VisualCapability;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.editor.VisualEditOperation;
import com.toolbox.tools.editor.VisualEditTransaction;

import java.util.Arrays;

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
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        sp.topMargin = UiKit.dp(c, 42);
        addView(screen, sp);

        TextView eyebrow = UiKit.teks(
                c,
                "BERANDA  •  PREVIEW LANGSUNG",
                10f,
                UiKit.NEON
        );
        screen.addView(eyebrow);

        TextView title = UiKit.judul(
                c,
                kernel.declarativeRuntime().value(
                        "ui.screen.home.title",
                        "Bangun aplikasi secara visual"
                ),
                22f
        );
        title.setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 4));
        screen.addView(title);

        TextView sub = UiKit.teks(
                c,
                kernel.declarativeRuntime().value(
                        "ui.screen.home.subtitle",
                        "Layar ini adalah surface yang sama saat Edit aktif maupun nonaktif."
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
                "Pilih objek lalu ubah properti dari panel kontekstual.",
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
                UiKit.dp(c, 112)
        ));

        TextView guide = UiKit.teks(
                c,
                "Area layout responsif • seret tombol saat Edit aktif",
                10f,
                UiKit.TEKS_REDUP
        );
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                UiKit.dp(c, 28),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        gp.topMargin = UiKit.dp(c, 8);
        freeArea.addView(guide, gp);

        primaryButton = UiKit.judul(
                c,
                kernel.declarativeRuntime().value(
                        "ui.object.home.primary.text",
                        "Buka Detail"
                ),
                13f
        );
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setTextColor(UiKit.LATAR);
        primaryButton.setBackground(UiKit.kartuPx(
                c,
                UiKit.NEON,
                UiKit.NEON,
                14,
                1
        ));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                UiKit.dp(c, 148),
                UiKit.dp(c, 46)
        );
        bp.leftMargin = UiKit.dp(c, 18);
        bp.topMargin = UiKit.dp(c, 50);
        freeArea.addView(primaryButton, bp);
        primaryButton.setOnTouchListener(this::handlePrimaryTouch);
        primaryButton.setOnClickListener(v -> {
            if (!dragging) selectPrimary();
        });

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

        UiKit.ruang(screen, c, 16);

        TextView hint = UiKit.teks(
                c,
                "Edit OFF: objek menjalankan aksi • Edit ON: objek dipilih dan diedit.",
                11f,
                UiKit.TEKS_REDUP
        );
        screen.addView(hint);
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
                    UiKit.NEON,
                    UiKit.NEON_BIRU,
                    14,
                    2
            ));
            if (listener != null) listener.onSelected("object.home.primary");
        } catch (RuntimeException ignored) {
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
            selectPrimary();
        } catch (RuntimeException ignored) {
        }
    }
}
