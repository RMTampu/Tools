package com.toolbox.tools.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ProjectResourceLayout {
    private static final List<String> DOMAINS =
            Collections.unmodifiableList(Arrays.asList(
                    "screens",
                    "logic",
                    "data",
                    "bindings",
                    "assets",
                    "styles",
                    "localization",
                    "metadata"
            ));

    private ProjectResourceLayout() {}

    public static List<String> domainDirectories() {
        return DOMAINS;
    }

    public static String relativePath(String stableId) {
        String id = StableId.require(stableId, "resourceId");
        String file = ProjectDefinitionCodec.resourceFileName(id);

        if (id.startsWith("ui.")
                || id.startsWith("screen.")
                || id.startsWith("object.")
                || id.startsWith("layout.")) {
            return "screens/"
                    + screenBucket(id)
                    + "/"
                    + file;
        }
        if (id.startsWith("logic.")
                || id.startsWith("flow.")
                || id.startsWith("event.")
                || id.startsWith("action.")) {
            return "logic/" + file;
        }
        if (id.startsWith("data.")) {
            return "data/" + file;
        }
        if (id.startsWith("binding.")) {
            return "bindings/" + file;
        }
        if (id.startsWith("asset.")) {
            return "assets/" + file;
        }
        if (id.startsWith("style.")
                || id.startsWith("theme.")
                || id.startsWith("token.")) {
            return "styles/" + file;
        }
        if (id.startsWith("locale.")
                || id.startsWith("localization.")
                || id.startsWith("i18n.")) {
            return "localization/" + file;
        }
        return "metadata/" + file;
    }

    public static boolean validRelativePath(
            String stableId,
            String relativePath
    ) {
        if (relativePath == null
                || relativePath.startsWith("/")
                || relativePath.contains("..")
                || relativePath.contains("\\")) {
            return false;
        }
        return relativePath(stableId).equals(relativePath);
    }

    private static String screenBucket(String id) {
        String[] parts = id.split("\\.");
        String candidate = "_global";

        if (id.startsWith("ui.object.") && parts.length >= 3) {
            candidate = parts[2];
        } else if (id.startsWith("ui.screen.")
                && parts.length >= 3) {
            candidate = parts[2];
        } else if ((id.startsWith("screen.")
                || id.startsWith("object.")
                || id.startsWith("layout."))
                && parts.length >= 2) {
            candidate = parts[1];
        }

        String safe = candidate
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        if (safe.isEmpty()) safe = "_global";
        return safe.length() > 64
                ? safe.substring(0, 64)
                : safe;
    }
}
