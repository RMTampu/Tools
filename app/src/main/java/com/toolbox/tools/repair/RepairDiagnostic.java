package com.toolbox.tools.repair;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class RepairDiagnostic {
    private final String code;
    private final String message;

    public RepairDiagnostic(String code, String message) {
        this.code = StableId.require(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String code() { return code; }
    public String message() { return message; }
}
