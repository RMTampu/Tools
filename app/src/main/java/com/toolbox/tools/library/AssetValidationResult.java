package com.toolbox.tools.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssetValidationResult {
    private final List<String> errors;

    private AssetValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static AssetValidationResult of(List<String> errors) {
        return new AssetValidationResult(errors);
    }

    public boolean isPass() { return errors.isEmpty(); }
    public List<String> errors() { return errors; }
    public String message() { return isPass() ? "PASS" : String.join(",", errors); }
}
