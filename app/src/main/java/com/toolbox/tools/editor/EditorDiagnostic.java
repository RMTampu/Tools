package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class EditorDiagnostic {
    private final String code;
    private final String subjectId;
    private final String message;

    public EditorDiagnostic(
            String code,
            String subjectId,
            String message
    ) {
        this.code = StableId.require(code, "code");
        this.subjectId = StableId.require(subjectId, "subjectId");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String code() { return code; }
    public String subjectId() { return subjectId; }
    public String message() { return message; }
}
