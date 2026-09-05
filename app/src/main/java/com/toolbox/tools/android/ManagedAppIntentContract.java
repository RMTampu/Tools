package com.toolbox.tools.android;

import android.content.Intent;
import android.net.Uri;

import com.toolbox.tools.protocol.ManagedAppProtocol;

import java.util.Set;

public final class ManagedAppIntentContract {
    public static final String ACTION_AWARE = "com.toolbox.AWARE";
    public static final String ACTION_DESCRIBE = "com.toolbox.DESCRIBE";
    public static final String ACTION_PREVIEW_PATCH =
            "com.toolbox.PREVIEW_PATCH";
    public static final String ACTION_APPLY_PATCH =
            "com.toolbox.APPLY_PATCH";
    public static final String ACTION_HEALTH = "com.toolbox.HEALTH";
    public static final String ACTION_ROLLBACK = "com.toolbox.ROLLBACK";

    public static final String EXTRA_PROTOCOL_VERSION =
            "com.toolbox.extra.PROTOCOL_VERSION";
    public static final String EXTRA_SESSION_ID =
            "com.toolbox.extra.SESSION_ID";
    public static final String EXTRA_PROJECT_ID =
            "com.toolbox.extra.PROJECT_ID";
    public static final String EXTRA_REVISION =
            "com.toolbox.extra.REVISION";
    public static final String EXTRA_PATCH_URI =
            "com.toolbox.extra.PATCH_URI";

    private ManagedAppIntentContract() {}

    public static Intent request(
            ManagedAppProtocol.Session session,
            ManagedAppProtocol.RequestType type,
            Uri patchUri
    ) {
        if (session == null || type == null) {
            throw new IllegalArgumentException(
                    "managed app request incomplete"
            );
        }
        if (!session.can(type)) {
            throw new IllegalArgumentException(
                    "request tidak diizinkan capability"
            );
        }
        String action = action(type);
        Intent intent = new Intent(action);
        intent.setPackage(session.target().packageName());
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(
                EXTRA_PROTOCOL_VERSION,
                ManagedAppProtocol.CURRENT_VERSION
        );
        intent.putExtra(
                EXTRA_SESSION_ID,
                session.sessionId()
        );
        intent.putExtra(
                EXTRA_PROJECT_ID,
                session.target().projectId()
        );
        intent.putExtra(
                EXTRA_REVISION,
                session.target().revision()
        );
        if (patchUri != null) {
            intent.setData(patchUri);
            intent.putExtra(EXTRA_PATCH_URI, patchUri.toString());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }

    private static String action(
            ManagedAppProtocol.RequestType type
    ) {
        switch (type) {
            case DESCRIBE:
                return ACTION_DESCRIBE;
            case PREVIEW_PATCH:
                return ACTION_PREVIEW_PATCH;
            case APPLY_PATCH:
                return ACTION_APPLY_PATCH;
            case HEALTH:
                return ACTION_HEALTH;
            case ROLLBACK:
                return ACTION_ROLLBACK;
            default:
                throw new IllegalArgumentException(
                        "request type unsupported"
                );
        }
    }
}
