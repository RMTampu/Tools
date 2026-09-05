package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import com.toolbox.tools.core.AppKernel;

import java.util.Collections;
import java.util.LinkedHashMap;

public final class LogicGraphView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] nodes = new float[][]{
            {28, 36, 138, 104},
            {176, 36, 286, 104},
            {176, 142, 286, 210},
            {28, 142, 138, 210}
    };
    private final String[] titles = new String[]{
            "Saat Tombol Ditekan",
            "Periksa Kondisi",
            "Buka Layar Detail",
            "Tampilkan Pesan"
    };
    private final AppKernel kernel;
    private int selected = -1;
    private float lastX;
    private float lastY;

    public LogicGraphView(Context context) {
        this(context, null);
    }

    public LogicGraphView(Context context, AppKernel kernel) {
        super(context);
        this.kernel = kernel;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(UiKit.dp(context, 2));
        stroke.setColor(UiKit.NEON_BIRU);
        text.setColor(UiKit.TEKS);
        text.setTextSize(UiKit.dp(context, 11));
        setBackground(UiKit.kartuPx(
                context,
                UiKit.PERMUKAAN,
                UiKit.GARIS,
                18,
                1
        ));
        setMinimumHeight(UiKit.dp(context, 270));
        restorePositions();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;

        drawConnection(canvas, nodes[0], nodes[1], d);
        drawConnection(canvas, nodes[1], nodes[2], d);
        drawConnection(canvas, nodes[1], nodes[3], d);

        for (int i = 0; i < nodes.length; i++) {
            float[] n = nodes[i];
            fill.setColor(i == selected
                    ? Color.rgb(12, 68, 61)
                    : UiKit.PERMUKAAN_2);
            stroke.setColor(i == selected ? UiKit.NEON : UiKit.GARIS);
            canvas.drawRoundRect(
                    n[0] * d,
                    n[1] * d,
                    n[2] * d,
                    n[3] * d,
                    UiKit.dp(getContext(), 14),
                    UiKit.dp(getContext(), 14),
                    fill
            );
            canvas.drawRoundRect(
                    n[0] * d,
                    n[1] * d,
                    n[2] * d,
                    n[3] * d,
                    UiKit.dp(getContext(), 14),
                    UiKit.dp(getContext(), 14),
                    stroke
            );

            fill.setColor(i == 0 ? UiKit.NEON : UiKit.NEON_BIRU);
            canvas.drawCircle(
                    (n[0] + 14) * d,
                    (n[1] + 16) * d,
                    UiKit.dp(getContext(), 4),
                    fill
            );

            text.setColor(UiKit.TEKS);
            text.setTextSize(UiKit.dp(getContext(), 10));
            canvas.drawText(
                    titles[i],
                    (n[0] + 12) * d,
                    (n[1] + 40) * d,
                    text
            );

            text.setColor(UiKit.TEKS_REDUP);
            text.setTextSize(UiKit.dp(getContext(), 8.5f));
            canvas.drawText(
                    i == 0 ? "EVENT" : i == 1 ? "KONDISI" : "AKSI",
                    (n[0] + 12) * d,
                    (n[1] + 57) * d,
                    text
            );
        }
    }

    private void drawConnection(
            Canvas canvas,
            float[] from,
            float[] to,
            float density
    ) {
        float startX = from[2] * density;
        float startY = ((from[1] + from[3]) / 2f) * density;
        float endX = to[0] * density;
        float endY = ((to[1] + to[3]) / 2f) * density;

        Path p = new Path();
        p.moveTo(startX, startY);
        float mid = (startX + endX) / 2f;
        p.cubicTo(mid, startY, mid, endY, endX, endY);
        stroke.setColor(UiKit.NEON_BIRU);
        stroke.setStrokeWidth(UiKit.dp(getContext(), 2));
        canvas.drawPath(p, stroke);

        fill.setColor(UiKit.NEON);
        canvas.drawCircle(endX, endY, UiKit.dp(getContext(), 3), fill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float d = getResources().getDisplayMetrics().density;
        float x = event.getX() / d;
        float y = event.getY() / d;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selected = hit(x, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (selected >= 0) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    float[] n = nodes[selected];
                    n[0] += dx; n[2] += dx;
                    n[1] += dy; n[3] += dy;
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                persistSelectedPosition();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void restorePositions() {
        if (kernel == null) return;
        for (int i = 0; i < nodes.length; i++) {
            String prefix = "logic.flow.home.node." + i;
            try {
                String sx = kernel.projectManager()
                        .current()
                        .resources()
                        .get(prefix + ".x.dp");
                String sy = kernel.projectManager()
                        .current()
                        .resources()
                        .get(prefix + ".y.dp");
                if (sx == null || sy == null) continue;
                float x = Float.parseFloat(sx);
                float y = Float.parseFloat(sy);
                float width = nodes[i][2] - nodes[i][0];
                float height = nodes[i][3] - nodes[i][1];
                nodes[i][0] = x;
                nodes[i][1] = y;
                nodes[i][2] = x + width;
                nodes[i][3] = y + height;
            } catch (RuntimeException ignored) {
                // Posisi invalid kembali ke layout bawaan.
            }
        }
    }

    private void persistSelectedPosition() {
        if (kernel == null || selected < 0) return;
        LinkedHashMap<String, String> update =
                new LinkedHashMap<>();
        String prefix = "logic.flow.home.node." + selected;
        update.put(
                prefix + ".x.dp",
                Float.toString(nodes[selected][0])
        );
        update.put(
                prefix + ".y.dp",
                Float.toString(nodes[selected][1])
        );
        kernel.projectManager().applyResourceTransaction(
                update,
                Collections.emptySet()
        );
    }

    private int hit(float x, float y) {
        for (int i = nodes.length - 1; i >= 0; i--) {
            float[] n = nodes[i];
            if (x >= n[0] && x <= n[2] && y >= n[1] && y <= n[3]) {
                return i;
            }
        }
        return -1;
    }
}
