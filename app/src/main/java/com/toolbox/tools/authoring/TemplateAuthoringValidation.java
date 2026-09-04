package com.toolbox.tools.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TemplateAuthoringValidation {
    private final List<String> issues;

    public TemplateAuthoringValidation(List<String> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean isPass() { return issues.isEmpty(); }
    public List<String> issues() { return issues; }
    public String message() {
        return isPass() ? "PASS" : String.join(",", issues);
    }
}
