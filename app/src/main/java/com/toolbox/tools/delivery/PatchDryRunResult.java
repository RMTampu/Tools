package com.toolbox.tools.delivery;

import com.toolbox.tools.core.ProjectState;

public final class PatchDryRunResult {
    private final boolean pass;
    private final String reason;
    private final ProjectState candidate;

    public PatchDryRunResult(
            boolean pass,
            String reason,
            ProjectState candidate
    ) {
        this.pass = pass;
        this.reason = reason == null ? "" : reason;
        this.candidate = candidate;
    }

    public boolean isPass() { return pass; }
    public String reason() { return reason; }
    public ProjectState candidate() { return candidate; }
}
