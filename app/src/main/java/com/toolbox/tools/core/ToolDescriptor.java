package com.toolbox.tools.core;

import java.util.Objects;

public final class ToolDescriptor {
    private final String id;
    private final String name;
    private final String version;

    public ToolDescriptor(String id, String name, String version) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.version = requireText(version, "version");
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
