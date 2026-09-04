package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class DependencyRef {
    private final String dependencyId;
    private final VersionRange versionRange;
    private final boolean required;

    public DependencyRef(
            String dependencyId,
            VersionRange versionRange,
            boolean required
    ) {
        this.dependencyId = StableId.require(dependencyId, "dependencyId");
        this.versionRange = Objects.requireNonNull(versionRange, "versionRange");
        this.required = required;
    }

    public String dependencyId() { return dependencyId; }
    public VersionRange versionRange() { return versionRange; }
    public boolean required() { return required; }
}
