package com.toolbox.tools.core;

public final class RecoveryCandidate {
    public enum Kind {
        FINAL_RECOVERY_SNAPSHOT,
        LAST_VALID_RECOVERY,
        LAST_VALID_REVISION,
        OLDER_REVISION,
        DRAFT_RECOVERY
    }

    private final Kind kind;
    private final long revision;
    private final long sizeBytes;

    public RecoveryCandidate(Kind kind, long revision, long sizeBytes) {
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (revision < 0 || sizeBytes < 0) {
            throw new IllegalArgumentException("candidate values invalid");
        }
        this.kind = kind;
        this.revision = revision;
        this.sizeBytes = sizeBytes;
    }

    public Kind kind() {
        return kind;
    }

    public long revision() {
        return revision;
    }

    public long sizeBytes() {
        return sizeBytes;
    }
}
