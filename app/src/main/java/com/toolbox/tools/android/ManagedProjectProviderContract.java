package com.toolbox.tools.android;

public final class ManagedProjectProviderContract {
    public static final int VERSION = 1;

    public static final String METHOD_DESCRIBE =
            "toolbox.describe";
    public static final String METHOD_LOAD =
            "toolbox.load";
    public static final String METHOD_COMMIT =
            "toolbox.commit";
    public static final String METHOD_LOAD_REVISION =
            "toolbox.loadRevision";
    public static final String METHOD_RECOVER_REVISION =
            "toolbox.recoverRevision";
    public static final String METHOD_RECOVER_STATE =
            "toolbox.recoverState";

    public static final String EXTRA_PROJECT_ID =
            "projectId";
    public static final String EXTRA_PROJECT_PAYLOAD =
            "projectPayload";
    public static final String EXTRA_EXPECTED_REVISION =
            "expectedRevision";
    public static final String EXTRA_REVISION =
            "revision";
    public static final String EXTRA_PROTOCOL_VERSION =
            "protocolVersion";
    public static final String EXTRA_WRITABLE =
            "writable";
    public static final String EXTRA_ERROR =
            "error";

    private ManagedProjectProviderContract() {}
}
