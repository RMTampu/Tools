package com.toolbox.tools.library;

import java.util.Objects;

public final class VersionRange {
    private final VersionNumber minInclusive;
    private final VersionNumber maxExclusive;

    public VersionRange(VersionNumber minInclusive, VersionNumber maxExclusive) {
        this.minInclusive = Objects.requireNonNull(minInclusive, "minInclusive");
        this.maxExclusive = Objects.requireNonNull(maxExclusive, "maxExclusive");
        if (minInclusive.compareTo(maxExclusive) >= 0) {
            throw new IllegalArgumentException("version range empty");
        }
    }

    public static VersionRange majorCompatible(VersionNumber base) {
        Objects.requireNonNull(base, "base");
        return new VersionRange(
                base,
                new VersionNumber(base.major() + 1, 0, 0)
        );
    }

    public boolean contains(VersionNumber version) {
        Objects.requireNonNull(version, "version");
        return version.compareTo(minInclusive) >= 0
                && version.compareTo(maxExclusive) < 0;
    }

    public VersionNumber minInclusive() { return minInclusive; }
    public VersionNumber maxExclusive() { return maxExclusive; }
}
