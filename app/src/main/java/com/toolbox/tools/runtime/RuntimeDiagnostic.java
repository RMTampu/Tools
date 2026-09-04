package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class RuntimeDiagnostic {
    private final DiagnosticCode code;
    private final String subjectId;
    private final String message;

    public RuntimeDiagnostic(
            DiagnosticCode code,
            String subjectId,
            String message
    ) {
        this.code = Objects.requireNonNull(code, "code");
        this.subjectId = StableId.require(subjectId, "subjectId");
        this.message = Objects.requireNonNull(message, "message");
    }

    public DiagnosticCode code() { return code; }
    public String subjectId() { return subjectId; }
    public String message() { return message; }
}
