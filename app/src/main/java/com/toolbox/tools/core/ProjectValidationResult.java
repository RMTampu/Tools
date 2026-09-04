package com.toolbox.tools.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProjectValidationResult {
    private final List<String> errors;

    private ProjectValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static ProjectValidationResult of(List<String> errors) {
        return new ProjectValidationResult(errors);
    }

    public boolean isPass() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return errors;
    }

    public String message() {
        return isPass() ? "PASS" : String.join(",", errors);
    }
}
