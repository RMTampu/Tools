package com.toolbox.tools.core;

import java.util.Objects;
import java.util.regex.Pattern;

public final class StableId {
    public static final int MAX_LENGTH = 128;
    private static final Pattern VALID =
            Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    private StableId() {
    }

    public static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > MAX_LENGTH || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid Stable ID");
        }
        return value;
    }
}
