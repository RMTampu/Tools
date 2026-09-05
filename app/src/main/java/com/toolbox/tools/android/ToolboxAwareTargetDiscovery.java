package com.toolbox.tools.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.toolbox.tools.product.ProductCompletionServices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            List<String> capabilities = new ArrayList<>(Arrays.asList(
                    "ui",
                    "logic",
                    "data",
                    "binding",
                    "asset"
            ));
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
}
