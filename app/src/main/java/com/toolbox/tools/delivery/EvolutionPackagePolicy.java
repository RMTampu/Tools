package com.toolbox.tools.delivery;

public final class EvolutionPackagePolicy {
    private EvolutionPackagePolicy() {}

    public static void requireProductionSchema(int schemaVersion) {
        if (schemaVersion != PatchManifest.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "app.patch produksi wajib schema V2"
            );
        }
    }

    public static boolean isProductionSchema(int schemaVersion) {
        return schemaVersion == PatchManifest.CURRENT_SCHEMA_VERSION;
    }
}
