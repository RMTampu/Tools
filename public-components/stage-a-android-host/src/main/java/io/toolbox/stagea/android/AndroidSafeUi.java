package io.toolbox.stagea.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

public final class AndroidSafeUi {
    public interface Actions {
        String verifyIntegrity();
        String retryBootstrap();
        String enterReadOnly();
        String exportSanitizedDiagnostics();
        boolean canRestoreKnownGood();
        String restoreKnownGood();
        boolean canQuarantine();
        String quarantine();
    }

    private AndroidSafeUi() {}

    public static View render(Activity activity, StageAContracts.SafeUiModel model, String statusText, Actions actions) {
        if (activity == null || model == null || actions == null) throw new NullPointerException();
        if (!model.visible() || !model.restricted()) {
            throw new StageAContracts.StageAException("safe.ui.not.required", "Safe UI may render only for restricted recovery states");
        }
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(activity, 24);
        body.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(activity);
        title.setText("ToolBox — Mode Aman");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(22f);
        body.addView(title, matchWrap());

        TextView state = new TextView(activity);
        state.setText(indonesianState(model.recoveryState()));
        state.setContentDescription("Status mode aman " + model.recoveryState().name());
        state.setTextSize(17f);
        state.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
        body.addView(state, matchWrap());

        TextView detail = new TextView(activity);
        detail.setText(statusText == null || statusText.trim().isEmpty()
                ? "Aplikasi dibatasi sampai pemeriksaan keselamatan selesai."
                : statusText.trim());
        detail.setContentDescription("Ringkasan diagnostik aman");
        body.addView(detail, matchWrap());

        body.addView(button(activity, "Periksa kondisi aman", () -> detail.setText(safeMessage(actions.verifyIntegrity()))), matchWrap());
        body.addView(button(activity, "Cek status bootstrap", () -> detail.setText(safeMessage(actions.retryBootstrap()))), matchWrap());
        body.addView(button(activity, "Tetap di mode terbatas", () -> detail.setText(safeMessage(actions.enterReadOnly()))), matchWrap());
        body.addView(button(activity, "Ringkas diagnostik aman", () -> detail.setText(safeMessage(actions.exportSanitizedDiagnostics()))), matchWrap());

        Button restore = button(activity, "Pulihkan state aman", () -> detail.setText(safeMessage(actions.restoreKnownGood())));
        restore.setEnabled(actions.canRestoreKnownGood());
        body.addView(restore, matchWrap());

        Button quarantine = button(activity, "Karantina komponen bermasalah", () -> detail.setText(safeMessage(actions.quarantine())));
        quarantine.setEnabled(actions.canQuarantine());
        body.addView(quarantine, matchWrap());

        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    static String indonesianState(SafetyContracts.RecoveryState state) {
        switch (state) {
            case RECOVERY_REQUIRED: return "Pemulihan diperlukan";
            case SAFE_MODE: return "Mode aman aktif";
            case QUARANTINED: return "Runtime dikarantina";
            default: return "Status pemulihan terbatas";
        }
    }

    private static String safeMessage(String value) {
        if (value == null || value.trim().isEmpty()) return "Tidak ada detail tambahan.";
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }

    private interface Action { void run(); }

    private static Button button(Activity activity, String label, Action action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setContentDescription(label);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Activity activity, int value) {
        return Math.max(1, Math.round(value * activity.getResources().getDisplayMetrics().density));
    }
}
