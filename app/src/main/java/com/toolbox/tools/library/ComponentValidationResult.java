package com.toolbox.tools.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentValidationResult {
    private final List<String> errors;

    private ComponentValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static ComponentValidationResult of(List<String> errors) {
        return new ComponentValidationResult(errors);
    }

    public boolean isPass() { return errors.isEmpty(); }
    public List<String> errors() { return errors; }
    public String message() { return isPass() ? "PASS" : String.join(",", errors); }
}
