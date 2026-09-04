package com.toolbox.tools.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowValidationResult {
    private final List<String> issues;

    public FlowValidationResult(List<String> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean isPass() { return issues.isEmpty(); }
    public List<String> issues() { return issues; }
    public String message() { return isPass() ? "PASS" : String.join(",", issues); }
}
