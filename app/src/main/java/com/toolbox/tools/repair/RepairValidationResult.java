package com.toolbox.tools.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RepairValidationResult {
    private final List<RepairDiagnostic> diagnostics;

    public RepairValidationResult(List<RepairDiagnostic> diagnostics) {
        this.diagnostics = Collections.unmodifiableList(
                new ArrayList<>(diagnostics)
        );
    }

    public boolean isPass() { return diagnostics.isEmpty(); }
    public List<RepairDiagnostic> diagnostics() { return diagnostics; }
}
