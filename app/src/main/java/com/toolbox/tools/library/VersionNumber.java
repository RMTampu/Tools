package com.toolbox.tools.library;

import java.util.Objects;

public final class VersionNumber implements Comparable<VersionNumber> {
    private final int major;
    private final int minor;
    private final int patch;

    public VersionNumber(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version parts must be >= 0");
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static VersionNumber parse(String value) {
        Objects.requireNonNull(value, "version");
        if (!value.matches("0|[1-9]\\d*(\\.(0|[1-9]\\d*)){0,2}")) {
            throw new IllegalArgumentException("invalid semantic version");
        }
        String[] parts = value.split("\\.");
        int major = integer(parts[0]);
        int minor = parts.length > 1 ? integer(parts[1]) : 0;
        int patch = parts.length > 2 ? integer(parts[2]) : 0;
        return new VersionNumber(major, minor, patch);
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("version part invalid", error);
        }
    }

    public int major() { return major; }
    public int minor() { return minor; }
    public int patch() { return patch; }

    @Override
    public int compareTo(VersionNumber other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;
        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VersionNumber)) return false;
        VersionNumber that = (VersionNumber) other;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
