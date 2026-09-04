package com.toolbox.tools.core;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class WorkspaceSnapshot {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_VALUE_LENGTH = 4096;

    private static final Pattern WORKSPACE_ID =
            Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final String workspaceId;
    private final int schemaVersion;
    private final long revision;
    private final Map<String, String> values;

    private WorkspaceSnapshot(
            String workspaceId,
            int schemaVersion,
            long revision,
            Map<String, String> values
    ) {
        this.workspaceId = requireWorkspaceId(workspaceId);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + schemaVersion);
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0");
        }
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many workspace entries");
        }
        TreeMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = requireKey(entry.getKey());
            String value = requireValue(entry.getValue());
            if (copy.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate workspace key: " + key);
            }
        }
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.values = Collections.unmodifiableMap(copy);
    }

    public static WorkspaceSnapshot create(String workspaceId) {
        return new WorkspaceSnapshot(
                workspaceId,
                CURRENT_SCHEMA_VERSION,
                0,
                Collections.emptyMap()
        );
    }

    public static WorkspaceSnapshot restore(
            String workspaceId,
            int schemaVersion,
            long revision,
            Map<String, String> values
    ) {
        return new WorkspaceSnapshot(workspaceId, schemaVersion, revision, values);
    }

    public WorkspaceSnapshot withValue(String key, String value, long newRevision) {
        TreeMap<String, String> next = new TreeMap<>(values);
        next.put(requireKey(key), requireValue(value));
        return new WorkspaceSnapshot(workspaceId, schemaVersion, newRevision, next);
    }

    public WorkspaceSnapshot withoutValue(String key, long newRevision) {
        TreeMap<String, String> next = new TreeMap<>(values);
        next.remove(requireKey(key));
        return new WorkspaceSnapshot(workspaceId, schemaVersion, newRevision, next);
    }

    public WorkspaceSnapshot withRevision(long newRevision) {
        return new WorkspaceSnapshot(workspaceId, schemaVersion, newRevision, values);
    }

    public String workspaceId() {
        return workspaceId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public long revision() {
        return revision;
    }

    public Map<String, String> values() {
        return values;
    }

    public String value(String key, String fallback) {
        String value = values.get(requireKey(key));
        return value == null ? fallback : value;
    }

    private static String requireWorkspaceId(String value) {
        Objects.requireNonNull(value, "workspaceId");
        if (!WORKSPACE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid workspaceId");
        }
        return value;
    }

    private static String requireKey(String value) {
        Objects.requireNonNull(value, "key");
        if (value.length() > MAX_KEY_LENGTH || !KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid workspace key");
        }
        return value;
    }

    private static String requireValue(String value) {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("workspace value too large");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkspaceSnapshot)) {
            return false;
        }
        WorkspaceSnapshot that = (WorkspaceSnapshot) other;
        return schemaVersion == that.schemaVersion
                && revision == that.revision
                && workspaceId.equals(that.workspaceId)
                && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, schemaVersion, revision, values);
    }
}
