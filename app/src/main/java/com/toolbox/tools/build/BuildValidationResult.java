package com.toolbox.tools.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BuildValidationResult {
    private final List<BuildDiagnostic> diagnostics;

    public BuildValidationResult(List<BuildDiagnostic> diagnostics) {
        this.diagnostics = Collections.unmodifiableList(
                new ArrayList<>(diagnostics)
        );
    }

    public boolean isPass() { return diagnostics.isEmpty(); }
    public List<BuildDiagnostic> diagnostics() { return diagnostics; }

    public String message() {
        if (diagnostics.isEmpty()) return "PASS";
        StringBuilder out = new StringBuilder();
        for (BuildDiagnostic diagnostic : diagnostics) {
            if (out.length() > 0) out.append(',');
            out.append(diagnostic.code());
        }
        return out.toString();
    }
}
