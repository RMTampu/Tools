package com.toolbox.tools.authoring;

public enum DraftLifecycle {
    DRAFT,
    VALIDATED,
    PUBLISHED,
    DISCARDED;

    public boolean terminal() {
        return this == PUBLISHED || this == DISCARDED;
    }
}
