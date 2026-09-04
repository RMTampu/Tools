package com.toolbox.tools.core;

import java.io.IOException;

public final class StaleWriteException extends IOException {
    public StaleWriteException(long expected, long actual) {
        super("STALE_WRITE expected=" + expected + " actual=" + actual);
    }
}
