package com.toolbox.tools.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import com.toolbox.tools.product.ProductCompletionServices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ToolboxAwareTargetDiscovery {
    public static final String ACTION_TOOLBOX_AWARE = "com.toolbox.AWARE";

    private ToolboxAwareTargetDiscovery() {}

    public static int discover(
            Context context,
            ProductCompletionServices.InstalledTargetBridge bridge
    ) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(ACTION_TOOLBOX_AWARE);
        List<ResolveInfo> results = pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
                        | PackageManager.GET_META_DATA
        );
        int added = 0;
        for (ResolveInfo info : results) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            CharSequence labelValue = info.loadLabel(pm);
            String label = labelValue == null
                    ? packageName
                    : labelValue.toString();
            List<String> capabilities = declaredCapabilities(
                    info.activityInfo.metaData
            );
            if (capabilities.isEmpty()) {
                continue;
            }
            try {
                bridge.registerAwareTarget(
                        packageName,
                        label,
                        capabilities
                );
                added++;
            } catch (IllegalArgumentException duplicateOrInvalid) {
                // Existing target remains authoritative for this process.
            }
        }
        return added;
    }

    private static List<String> declaredCapabilities(Bundle metaData) {
        if (metaData == null) return java.util.Collections.emptyList();
        String raw = metaData.getString("com.toolbox.CAPABILITIES", "");
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(
                "ui", "logic", "data", "binding", "asset"
        ));
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (allowed.contains(value)) out.add(value);
        }
        return new ArrayList<>(out);
    }
}
