package com.toolbox.tools.core;

public final class RecoveryCandidate {
    public enum Kind {
        FINAL_RECOVERY_SNAPSHOT,
        LAST_VALID_RECOVERY,
        LAST_VALID_REVISION,
        OLDER_REVISION,
        DRAFT_RECOVERY
    }

    public enum Retention {
        REQUIRED,
        IN_USE,
        DELETABLE
    }

    private final Kind kind;
    private final long revision;
    private final long sizeBytes;
    private final long createdAt;

    public RecoveryCandidate(
            Kind kind,
            long revision,
            long sizeBytes
    ) {
        this(kind, revision, sizeBytes, 0);
    }

    public RecoveryCandidate(
            Kind kind,
            long revision,
            long sizeBytes,
            long createdAt
    ) {
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (revision < 0
                || sizeBytes < 0
                || createdAt < 0) {
            throw new IllegalArgumentException(
                    "candidate values invalid"
            );
        }
        this.kind = kind;
        this.revision = revision;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    public Kind kind() { return kind; }
    public long revision() { return revision; }
    public long sizeBytes() { return sizeBytes; }
    public long createdAt() { return createdAt; }

    public Retention retention() {
        switch (kind) {
            case OLDER_REVISION:
                return Retention.DELETABLE;
            case DRAFT_RECOVERY:
                return Retention.IN_USE;
            case FINAL_RECOVERY_SNAPSHOT:
            case LAST_VALID_RECOVERY:
            case LAST_VALID_REVISION:
            default:
                return Retention.REQUIRED;
        }
    }

    public boolean deletable() {
        return retention() == Retention.DELETABLE;
    }
}
