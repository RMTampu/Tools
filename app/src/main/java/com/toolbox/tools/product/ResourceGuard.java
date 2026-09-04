package com.toolbox.tools.product;

import com.toolbox.tools.authoring.AuthoringSection;
import java.util.Objects;

public final class ResourceGuard {
    private AuthoringSection heavyActive = AuthoringSection.UI;
    private long memoryBudgetBytes = 192L * 1024L * 1024L;
    private int activeScreenCount = 1;

    public synchronized void activate(AuthoringSection section) {
        heavyActive = Objects.requireNonNull(section, "section");
        activeScreenCount = 1;
    }

    public synchronized AuthoringSection heavyActive() {
        return heavyActive;
    }

    public synchronized void setMemoryBudgetBytes(long value) {
        if (value < 32L * 1024L * 1024L) {
            throw new IllegalArgumentException("budget terlalu kecil");
        }
        memoryBudgetBytes = value;
    }

    public synchronized long memoryBudgetBytes() {
        return memoryBudgetBytes;
    }

    public synchronized boolean invariantPass() {
        return activeScreenCount == 1 && heavyActive != null;
    }
}
