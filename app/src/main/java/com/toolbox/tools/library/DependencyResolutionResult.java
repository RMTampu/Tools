package com.toolbox.tools.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DependencyResolutionResult {
    private final List<String> issues;

    public DependencyResolutionResult(List<String> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean isPass() { return issues.isEmpty(); }
    public List<String> issues() { return issues; }
    public String message() { return isPass() ? "PASS" : String.join(",", issues); }
}
