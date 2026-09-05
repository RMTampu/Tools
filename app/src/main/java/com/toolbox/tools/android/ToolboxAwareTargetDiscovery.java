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

/**
 * Legacy class name retained for binary/source compatibility.
 *
 * Discovery is no longer "ToolBox-aware only": it discovers explicit
 * editing doors and then derives only the capabilities each target declares.
 */
public final class ToolboxAwareTargetDiscovery {
    public static final String ACTION_TOOLBOX_AWARE = "com.toolbox.AWARE";
    public static final String ACTION_DESCRIBE = "com.toolbox.DESCRIBE";
    public static final String MIME_TOOLBOX_PROJECT =
            "application/vnd.toolbox.project+json";

    private ToolboxAwareTargetDiscovery() {}

    public static int discover(
            Context context,
            ProductCompletionServices.InstalledTargetBridge bridge
    ) {
        PackageManager pm = context.getPackageManager();
        bridge.clear();

        LinkedHashSet<String> before = new LinkedHashSet<>();
        discoverManaged(pm, ACTION_DESCRIBE, bridge, before);
        discoverManaged(pm, ACTION_TOOLBOX_AWARE, bridge, before);
        discoverGenericEdit(pm, bridge, before);
        return bridge.all().size();
    }

    private static void discoverManaged(
            PackageManager pm,
            String action,
            ProductCompletionServices.InstalledTargetBridge bridge,
            Set<String> seen
    ) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> results = pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
                        | PackageManager.GET_META_DATA
        );
        for (ResolveInfo info : results) {
            if (info.activityInfo == null
                    || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            Bundle meta = info.activityInfo.metaData;
            List<String> capabilities = declaredCapabilities(meta);
            int protocolVersion = meta == null
                    ? -1
                    : meta.getInt(
                            "com.toolbox.PROTOCOL_VERSION",
                            -1
                    );
            if (protocolVersion != 1 || capabilities.isEmpty()) {
                continue;
            }
            String projectId = meta.getString(
                    "com.toolbox.PROJECT_ID",
                    defaultProjectId(packageName)
            );
            long revision = meta.getLong(
                    "com.toolbox.REVISION",
                    0
            );
            String providerAuthority = meta.getString(
                    "com.toolbox.PROVIDER_AUTHORITY",
                    ""
            );
            String baselineApkSha256 = meta.getString(
                    "com.toolbox.BASELINE_APK_SHA256",
                    ""
            );
            if (baselineApkSha256 != null) {
                baselineApkSha256 =
                        baselineApkSha256.trim()
                                .toLowerCase(Locale.ROOT);
                if (!baselineApkSha256.matches(
                        "[0-9a-f]{64}"
                )) {
                    baselineApkSha256 = "";
                }
            }
            boolean writable = false;
            if (!providerAuthority.trim().isEmpty()) {
                android.content.pm.ProviderInfo provider =
                        pm.resolveContentProvider(
                                providerAuthority,
                                PackageManager.GET_META_DATA
                        );
                writable = provider != null
                        && provider.exported
                        && packageName.equals(provider.packageName);
                if (!writable) providerAuthority = "";
            }

            String label = label(pm, info, packageName);
            try {
                bridge.registerTarget(
                        packageName,
                        label,
                        capabilities,
                        protocolVersion,
                        projectId,
                        revision,
                        ProductCompletionServices
                                .InstalledTargetBridge
                                .DOOR_MANAGED_RUNTIME,
                        writable,
                        providerAuthority,
                        baselineApkSha256
                );
                seen.add(packageName);
            } catch (IllegalArgumentException ignored) {
                // Invalid declarations never become editable targets.
            }
        }
    }

    private static void discoverGenericEdit(
            PackageManager pm,
            ProductCompletionServices.InstalledTargetBridge bridge,
            Set<String> seen
    ) {
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType(MIME_TOOLBOX_PROJECT);
        List<ResolveInfo> results = pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
                        | PackageManager.GET_META_DATA
        );
        for (ResolveInfo info : results) {
            if (info.activityInfo == null
                    || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            Bundle meta = info.activityInfo.metaData;
            List<String> capabilities = declaredCapabilities(meta);
            if (capabilities.isEmpty()) {
                // Generic ACTION_EDIT proves an editing door but not arbitrary
                // rights inside the target. Keep it conservative/read-only.
                capabilities = Arrays.asList("ui", "asset");
            }
            boolean writable = meta != null
                    && meta.getBoolean(
                            "com.toolbox.GENERIC_EDIT_WRITABLE",
                            false
                    );
            try {
                bridge.registerTarget(
                        packageName,
                        label(pm, info, packageName),
                        capabilities,
                        0,
                        defaultProjectId(packageName),
                        0,
                        ProductCompletionServices
                                .InstalledTargetBridge
                                .DOOR_GENERIC_EDIT,
                        writable
                );
                seen.add(packageName);
            } catch (IllegalArgumentException ignored) {
                // Invalid generic door is ignored.
            }
        }
    }

    private static List<String> declaredCapabilities(Bundle metaData) {
        if (metaData == null) {
            return java.util.Collections.emptyList();
        }
        String raw = metaData.getString(
                "com.toolbox.CAPABILITIES",
                ""
        );
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(
                "ui",
                "logic",
                "data",
                "binding",
                "asset"
        ));
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (allowed.contains(value)) out.add(value);
        }
        return new ArrayList<>(out);
    }

    private static String label(
            PackageManager pm,
            ResolveInfo info,
            String fallback
    ) {
        CharSequence value = info.loadLabel(pm);
        String label = value == null
                ? fallback
                : value.toString().trim();
        return label.isEmpty() ? fallback : label;
    }

    private static String defaultProjectId(String packageName) {
        return "project."
                + packageName
                .toLowerCase(Locale.ROOT)
                .replace('.', '_');
    }
}
