package com.toolbox.tools.build;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class BuildDiagnostic {
    private final String code;
    private final String message;

    public BuildDiagnostic(String code, String message) {
        this.code = StableId.require(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String code() { return code; }
    public String message() { return message; }
}
