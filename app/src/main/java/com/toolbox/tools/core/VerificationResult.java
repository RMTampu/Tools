package com.toolbox.tools.core;

public final class VerificationResult {
    private final boolean pass;
    private final String message;

    private VerificationResult(boolean pass, String message) {
        this.pass = pass;
        this.message = message;
    }

    public static VerificationResult pass(String message) {
        return new VerificationResult(true, message);
    }

    public static VerificationResult fail(String message) {
        return new VerificationResult(false, message);
    }

    public boolean isPass() {
        return pass;
    }

    public String message() {
        return message;
    }
}
