package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    public static final int LATAR = Color.rgb(7,16,22);
    public static final int PERMUKAAN = Color.rgb(13,27,36);
    public static final int PERMUKAAN_2 = Color.rgb(17,40,50);
    public static final int GARIS = Color.rgb(36,70,80);
    public static final int NEON = Color.rgb(0,240,181);
    public static final int NEON_BIRU = Color.rgb(76,201,255);
    public static final int TEKS = Color.rgb(232,255,248);
    public static final int TEKS_REDUP = Color.rgb(143,184,174);
    public static final int BAHAYA = Color.rgb(255,107,122);
    public static final int PERINGATAN = Color.rgb(255,209,102);

    private UiKit() {}

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static TextView teks(
            Context c,
            String text,
            float sp,
            int color
    ) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFontFeatureSettings("kern");
        return v;
    }

    public static TextView judul(Context c, String text, float sp) {
        TextView v = teks(c, text, sp, TEKS);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    public static TextView tombol(
            Context c,
            String text,
            boolean aktif
    ) {
        TextView v = judul(c, text, 12.5f);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 40));
        v.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        v.setBackground(kartu(
                aktif ? Color.rgb(9,57,52) : PERMUKAAN_2,
                aktif ? NEON : GARIS,
                14,
                1
        ));
        v.setTextColor(aktif ? NEON : TEKS);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public static TextView chip(
            Context c,
            String text,
            boolean aktif
    ) {
        TextView v = teks(c, text, 11.5f, aktif ? LATAR : TEKS_REDUP);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        v.setBackground(kartu(
                aktif ? NEON : PERMUKAAN,
                aktif ? NEON : GARIS,
                999,
                1
        ));
        v.setClickable(true);
        return v;
    }

    public static LinearLayout baris(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    public static LinearLayout kolom(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    public static GradientDrawable kartu(
            int fill,
            int stroke,
            int radiusDp,
            int strokeDp
    ) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp);
        if (strokeDp > 0) d.setStroke(strokeDp, stroke);
        return d;
    }

    public static GradientDrawable kartuPx(
            Context c,
            int fill,
            int stroke,
            int radiusDp,
            int strokeDp
    ) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    public static LinearLayout.LayoutParams lp(
            int w,
            int h,
            float weight,
            int marginDp,
            Context c
    ) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h,weight);
        int m = dp(c, marginDp);
        p.setMargins(m,m,m,m);
        return p;
    }

    public static FrameLayout.LayoutParams flp(
            int w,
            int h,
            int gravity,
            int marginDp,
            Context c
    ) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w,h,gravity);
        int m = dp(c, marginDp);
        p.setMargins(m,m,m,m);
        return p;
    }

    public static void ruang(LinearLayout parent, Context c, int dp) {
        View v = new View(c);
        parent.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(c, dp)
        ));
    }

    public static TextView labelBagian(Context c, String text) {
        TextView v = judul(c, text, 12f);
        v.setTextColor(NEON_BIRU);
        v.setPadding(0, dp(c, 6), 0, dp(c, 6));
        return v;
    }
}
