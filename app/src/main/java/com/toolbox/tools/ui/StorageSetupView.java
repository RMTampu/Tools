package com.toolbox.tools.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class StorageSetupView extends LinearLayout {
    public StorageSetupView(
            Context context,
            StoragePickerHost host
    ) {
        super(context);
        if (host == null) throw new NullPointerException("host");
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int pad = UiKit.dp(context, 28);
        setPadding(pad, pad, pad, pad);
        setBackgroundColor(UiKit.LATAR);

        TextView title = UiKit.judul(
                context,
                "Siapkan Penyimpanan ToolBox",
                23f
        );
        title.setTextColor(UiKit.NEON);
        title.setGravity(Gravity.CENTER);
        addView(title);

        TextView body = UiKit.teks(
                context,
                "Rancangan ToolBox mewajibkan source of truth "
                        + "berada di folder milik pengguna.\n\n"
                        + "Pilih atau buat satu folder ToolBox. "
                        + "Aplikasi akan membuat Projects, Assets, "
                        + "Templates, Exports, Snapshots, dan Backups "
                        + "di dalamnya. Izin SAF dipertahankan selama "
                        + "Android masih memberikannya.",
                12.5f,
                UiKit.TEKS_REDUP
        );
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                );
        bodyParams.topMargin = UiKit.dp(context, 14);
        addView(body, bodyParams);

        TextView status = UiKit.teks(
                context,
                "Status: " + host.storageTreeStatus(),
                11f,
                UiKit.TEKS
        );
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                );
        statusParams.topMargin = UiKit.dp(context, 18);
        addView(status, statusParams);

        TextView select = UiKit.tombol(
                context,
                "Pilih / Buat Folder ToolBox",
                true
        );
        select.setGravity(Gravity.CENTER);
        select.setContentDescription(
                "Pilih folder penyimpanan ToolBox"
        );
        select.setOnClickListener(
                v -> host.requestToolBoxStorageTree()
        );
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        UiKit.dp(context, 52)
                );
        buttonParams.topMargin = UiKit.dp(context, 20);
        addView(select, buttonParams);

        TextView note = UiKit.teks(
                context,
                "Tidak ada project produksi yang dapat diedit atau "
                        + "dibuild sebelum storage user-owned terhubung.",
                10.5f,
                UiKit.TEKS_REDUP
        );
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                );
        noteParams.topMargin = UiKit.dp(context, 12);
        addView(note, noteParams);
    }
}
