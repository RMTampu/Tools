package com.toolbox.tools.product;

public final class ProductStoragePolicy {
    private ProductStoragePolicy() {}

    public static boolean requiresUserOwnedStorageSetup(
            boolean debugRuntime,
            boolean externalTargetActive,
            boolean persistedSafReadWriteAccess
    ) {
        if (externalTargetActive) {
            return false;
        }
        if (persistedSafReadWriteAccess) {
            return false;
        }
        // Debug CI may exercise app-private fallback, but a production
        // build must not present it as the project source of truth.
        return !debugRuntime;
    }
}
