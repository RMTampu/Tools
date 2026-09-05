package com.toolbox.tools.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.product.SafeModeController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class SafeRecoveryView extends LinearLayout {
    public interface Listener {
        void onSafeModeHealthyExitRequested();
    }

    private final SafeModeController safeMode;
    private final ProjectManager projects;
    private final VisibleWorkspaceStore visible;
    private final Listener listener;
    private final LinearLayout content;

    public SafeRecoveryView(
            Context context,
            SafeModeController safeMode,
            ProjectManager projects,
            VisibleWorkspaceStore visible,
            Listener listener
    ) {
        super(context);
        this.safeMode = java.util.Objects.requireNonNull(
                safeMode,
                "safeMode"
        );
        this.projects = java.util.Objects.requireNonNull(
                projects,
                "projects"
        );
        this.visible = java.util.Objects.requireNonNull(
                visible,
                "visible"
        );
        this.listener = java.util.Objects.requireNonNull(
                listener,
                "listener"
        );

        setOrientation(VERTICAL);
        setPadding(
                UiKit.dp(context, 18),
                UiKit.dp(context, 24),
                UiKit.dp(context, 18),
                UiKit.dp(context, 18)
        );
        setBackgroundColor(UiKit.LATAR);

        TextView title = UiKit.judul(
                context,
                "ToolBox • Mode Aman Independen",
                20f
        );
        title.setTextColor(UiKit.NEON_BIRU);
        addView(title);

        TextView subtitle = UiKit.teks(
                context,
                "UI pemulihan ini tidak membutuhkan engine editor/runtime.",
                11.5f,
                UiKit.TEKS_REDUP
        );
        subtitle.setPadding(
                0,
                UiKit.dp(context, 6),
                0,
                UiKit.dp(context, 12)
        );
        addView(subtitle);

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        addView(
                content,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );
        render();
    }

    private void render() {
        content.removeAllViews();

        LinearLayout card = UiKit.kolom(getContext());
        card.setPadding(
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14),
                UiKit.dp(getContext(), 14)
        );
        card.setBackground(UiKit.kartuPx(
                getContext(),
                UiKit.PERMUKAAN,
                UiKit.NEON_BIRU,
                18,
                1
        ));

        card.addView(UiKit.teks(
                getContext(),
                "Status: " + safeMode.statusIndonesia(),
                13f,
                UiKit.TEKS
        ));

        Map<String, String> snapshot =
                safeMode.diagnosticSnapshot();
        for (Map.Entry<String, String> entry
                : snapshot.entrySet()) {
            card.addView(UiKit.teks(
                    getContext(),
                    entry.getKey() + " = " + entry.getValue(),
                    10.5f,
                    UiKit.TEKS_REDUP
            ));
        }

        addButton(
                card,
                "Verifikasi Integritas Project",
                this::verifyIntegrity
        );
        addButton(
                card,
                "Buang Working State",
                this::discardWorking
        );
        addButton(
                card,
                "Inspeksi Read-Only",
                this::inspectReadOnly
        );
        addButton(
                card,
                "Ekspor Diagnostik",
                this::exportDiagnostic
        );
        addButton(
                card,
                "Kembali ke Workbench Jika Sehat",
                this::exitIfHealthy
        );

        content.addView(
                card,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void addButton(
            LinearLayout parent,
            String label,
            Runnable action
    ) {
        TextView button = UiKit.tombol(
                getContext(),
                label,
                false
        );
        button.setGravity(
                Gravity.START | Gravity.CENTER_VERTICAL
        );
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        UiKit.dp(getContext(), 46)
                );
        params.topMargin = UiKit.dp(getContext(), 8);
        parent.addView(button, params);
    }

    private void verifyIntegrity() {
        try {
            ProjectValidationResult result =
                    new ProjectValidator().validate(
                            projects.current()
                    );
            toast(
                    result.isPass()
                            ? "Integritas project: PASS"
                            : "Integritas project: GAGAL • "
                                + result.message()
            );
            if (!result.isPass()) {
                safeMode.enter();
            }
        } catch (RuntimeException error) {
            safeMode.enter();
            toast("Project tidak dapat diverifikasi.");
        }
        render();
    }

    private void discardWorking() {
        try {
            safeMode.discardWorkingChanges();
            toast("Working state dibuang.");
        } catch (IOException | RuntimeException error) {
            safeMode.enter();
            toast("Working state gagal dipulihkan.");
        }
        render();
    }

    private void inspectReadOnly() {
        try {
            toast(
                    "Read-only • revisi "
                            + projects.savedRevision()
                            + " • resource "
                            + projects.current()
                                .resources()
                                .size()
            );
        } catch (RuntimeException error) {
            toast("Read-only metadata tidak tersedia.");
        }
    }

    private void exportDiagnostic() {
        try {
            visible.ensureLayout();
            StringBuilder out = new StringBuilder();
            out.append("TOOLBOX_SAFE_DIAGNOSTIC_V1\n");
            for (Map.Entry<String, String> entry
                    : safeMode.diagnosticSnapshot().entrySet()) {
                out.append(entry.getKey())
                        .append('=')
                        .append(entry.getValue()
                                .replace("\n", " "))
                        .append('\n');
            }
            try {
                out.append("projectRevision=")
                        .append(projects.savedRevision())
                        .append('\n');
                out.append("resourceCount=")
                        .append(projects.current()
                                .resources()
                                .size())
                        .append('\n');
            } catch (RuntimeException error) {
                out.append("projectState=UNAVAILABLE\n");
            }

            String name = "safe-diagnostic-"
                    + System.currentTimeMillis()
                    + ".txt";
            visible.write(
                    VisibleWorkspaceStore.Area.EXPORTS,
                    name,
                    out.toString()
                        .getBytes(StandardCharsets.UTF_8)
            );
            toast("Diagnostik diekspor: Exports/" + name);
        } catch (Exception error) {
            toast("Ekspor diagnostik gagal.");
        }
    }

    private void exitIfHealthy() {
        try {
            ProjectValidationResult validation =
                    new ProjectValidator().validate(
                            projects.current()
                    );
            if (!validation.isPass()) {
                toast("Project belum sehat.");
                return;
            }
            safeMode.exitIfHealthy();
            listener.onSafeModeHealthyExitRequested();
        } catch (RuntimeException error) {
            toast("Pemulihan masih diperlukan.");
        }
    }

    private void toast(String value) {
        Toast.makeText(
                getContext(),
                value,
                Toast.LENGTH_LONG
        ).show();
    }
}
