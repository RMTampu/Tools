package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;

public final class ChangeToken {
    private final String originId;
    private final long version;

    public ChangeToken(String originId, long version) {
        this.originId = StableId.require(originId, "originId");
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        this.version = version;
    }

    public String originId() { return originId; }
    public long version() { return version; }

    public String fingerprint() {
        return originId + "#" + version;
    }
}
