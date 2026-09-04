package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class LibraryKey {
    private final LibraryItemType type;
    private final String stableId;
    private final VersionNumber version;

    public LibraryKey(
            LibraryItemType type,
            String stableId,
            VersionNumber version
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.stableId = StableId.require(stableId, "stableId");
        this.version = Objects.requireNonNull(version, "version");
    }

    public LibraryItemType type() { return type; }
    public String stableId() { return stableId; }
    public VersionNumber version() { return version; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LibraryKey)) return false;
        LibraryKey that = (LibraryKey) other;
        return type == that.type
                && stableId.equals(that.stableId)
                && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stableId, version);
    }
}
