package com.toolbox.tools.core;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProjectManifest {
    private final String projectId;
    private final int schemaVersion;
    private final int buildModelVersion;
    private final long revision;
    private final ProjectLifecycle lifecycle;
    private final String projectSha256;
    private final Map<String, String> resourceSha256;
    private final Set<String> dependencyRefs;

    private ProjectManifest(
            String projectId,
            int schemaVersion,
            int buildModelVersion,
            long revision,
            ProjectLifecycle lifecycle,
            String projectSha256,
            Map<String, String> resourceSha256,
            Set<String> dependencyRefs
    ) {
        this.projectId = StableId.require(projectId, "projectId");
        this.schemaVersion = schemaVersion;
        this.buildModelVersion = buildModelVersion;
        this.revision = revision;
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.projectSha256 = requireDigest(projectSha256);
        TreeMap<String, String> hashes = new TreeMap<>();
        for (Map.Entry<String, String> entry : resourceSha256.entrySet()) {
            hashes.put(
                    StableId.require(entry.getKey(), "resourceId"),
                    requireDigest(entry.getValue())
            );
        }
        this.resourceSha256 = Collections.unmodifiableMap(hashes);
        TreeSet<String> deps = new TreeSet<>();
        for (String dependency : dependencyRefs) {
            deps.add(StableId.require(dependency, "dependencyId"));
        }
        this.dependencyRefs = Collections.unmodifiableSet(deps);
    }

    public static ProjectManifest from(ProjectState state, String encodedProject) {
        TreeMap<String, String> resourceHashes = new TreeMap<>();
        for (Map.Entry<String, String> entry : state.resources().entrySet()) {
            resourceHashes.put(
                    entry.getKey(),
                    DigestUtils.sha256(entry.getValue().getBytes(StandardCharsets.UTF_8))
            );
        }
        return new ProjectManifest(
                state.projectId(),
                state.schemaVersion(),
                state.buildModelVersion(),
                state.revision(),
                state.lifecycle(),
                DigestUtils.sha256(encodedProject.getBytes(StandardCharsets.UTF_8)),
                resourceHashes,
                state.dependencyRefs()
        );
    }

    public String encode() {
        StringBuilder out = new StringBuilder();
        out.append("TBX_MANIFEST_V1\n");
        out.append("projectId=").append(projectId).append('\n');
        out.append("schemaVersion=").append(schemaVersion).append('\n');
        out.append("buildModelVersion=").append(buildModelVersion).append('\n');
        out.append("revision=").append(revision).append('\n');
        out.append("lifecycle=").append(lifecycle.name()).append('\n');
        out.append("projectSha256=").append(projectSha256).append('\n');
        out.append("resourceCount=").append(resourceSha256.size()).append('\n');
        for (Map.Entry<String, String> entry : resourceSha256.entrySet()) {
            out.append("resource.")
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append('\n');
        }
        out.append("dependencyCount=").append(dependencyRefs.size()).append('\n');
        for (String dependency : dependencyRefs) {
            out.append("dependency=").append(dependency).append('\n');
        }
        return out.toString();
    }

    public boolean verifies(ProjectState state, String encodedProject) {
        if (!projectId.equals(state.projectId())
                || schemaVersion != state.schemaVersion()
                || buildModelVersion != state.buildModelVersion()
                || revision != state.revision()
                || lifecycle != state.lifecycle()) {
            return false;
        }
        if (!projectSha256.equals(
                DigestUtils.sha256(encodedProject.getBytes(StandardCharsets.UTF_8)))) {
            return false;
        }
        if (!dependencyRefs.equals(state.dependencyRefs())) {
            return false;
        }
        if (resourceSha256.size() != state.resources().size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : state.resources().entrySet()) {
            String expected = resourceSha256.get(entry.getKey());
            String actual = DigestUtils.sha256(
                    entry.getValue().getBytes(StandardCharsets.UTF_8)
            );
            if (!actual.equals(expected)) {
                return false;
            }
        }
        return true;
    }

    public static ProjectManifest decode(String encoded) {
        if (encoded == null || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException("manifest invalid");
        }
        String[] lines = encoded.split("\n");
        if (lines.length < 9 || !"TBX_MANIFEST_V1".equals(lines[0])) {
            throw new IllegalArgumentException("manifest header invalid");
        }

        TreeMap<String, String> meta = new TreeMap<>();
        TreeMap<String, String> resources = new TreeMap<>();
        TreeSet<String> dependencies = new TreeSet<>();

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.startsWith("resource.")) {
                int equals = line.indexOf('=');
                if (equals <= "resource.".length()) {
                    throw new IllegalArgumentException("manifest resource invalid");
                }
                String id = line.substring("resource.".length(), equals);
                if (resources.put(id, line.substring(equals + 1)) != null) {
                    throw new IllegalArgumentException("manifest resource duplicate");
                }
            } else if (line.startsWith("dependency=")) {
                if (!dependencies.add(line.substring("dependency=".length()))) {
                    throw new IllegalArgumentException("manifest dependency duplicate");
                }
            } else {
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    throw new IllegalArgumentException("manifest metadata invalid");
                }
                String key = line.substring(0, equals);
                String value = line.substring(equals + 1);
                if (meta.put(key, value) != null) {
                    throw new IllegalArgumentException("manifest metadata duplicate");
                }
            }
        }

        String projectId = required(meta, "projectId");
        int schema = parseInt(required(meta, "schemaVersion"));
        int buildModel = parseInt(required(meta, "buildModelVersion"));
        long revision = parseLong(required(meta, "revision"));
        ProjectLifecycle lifecycle = ProjectLifecycle.valueOf(required(meta, "lifecycle"));
        String projectSha = required(meta, "projectSha256");
        int resourceCount = parseInt(required(meta, "resourceCount"));
        int dependencyCount = parseInt(required(meta, "dependencyCount"));
        if (meta.size() != 8
                || resourceCount != resources.size()
                || dependencyCount != dependencies.size()) {
            throw new IllegalArgumentException("manifest count mismatch");
        }
        return new ProjectManifest(
                projectId,
                schema,
                buildModel,
                revision,
                lifecycle,
                projectSha,
                resources,
                dependencies
        );
    }

    private static String required(Map<String, String> meta, String key) {
        String value = meta.get(key);
        if (value == null) {
            throw new IllegalArgumentException("manifest missing: " + key);
        }
        return value;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("manifest integer invalid", error);
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("manifest long invalid", error);
        }
    }

    private static String requireDigest(String value) {
        Objects.requireNonNull(value, "digest");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest invalid");
        }
        return value;
    }
}
