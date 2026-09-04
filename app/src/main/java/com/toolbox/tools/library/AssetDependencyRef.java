package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class AssetDependencyRef {
    private final String assetId;
    private final VersionRange versionRange;
    private final boolean required;

    public AssetDependencyRef(
            String assetId,
            VersionRange versionRange,
            boolean required
    ) {
        this.assetId = StableId.require(assetId, "assetId");
        this.versionRange = Objects.requireNonNull(versionRange, "versionRange");
        this.required = required;
    }

    public String assetId() { return assetId; }
    public VersionRange versionRange() { return versionRange; }
    public boolean required() { return required; }
}
