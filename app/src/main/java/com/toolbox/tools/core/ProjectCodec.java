package com.toolbox.tools.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProjectCodec {
    private static final String HEADER = "TBX_PROJECT_V1";
    private static final int MAX_ENCODED_BYTES = 16 * 1024 * 1024;

    public String encode(ProjectState state) {
        if (state == null) {
            throw new NullPointerException("state");
        }
        StringBuilder body = new StringBuilder();
        body.append(HEADER).append('\n');
        body.append("projectId=").append(b64(state.projectId())).append('\n');
        body.append("schemaVersion=").append(state.schemaVersion()).append('\n');
        body.append("buildModelVersion=").append(state.buildModelVersion()).append('\n');
        body.append("revision=").append(state.revision()).append('\n');
        body.append("lifecycle=").append(state.lifecycle().name()).append('\n');
        body.append("resourceCount=").append(state.resources().size()).append('\n');

        int referenceCount = 0;
        for (Set<String> targets : state.references().values()) {
            referenceCount += targets.size();
        }
        body.append("referenceCount=").append(referenceCount).append('\n');
        body.append("dependencyCount=").append(state.dependencyRefs().size()).append('\n');

        for (Map.Entry<String, String> entry : new TreeMap<>(state.resources()).entrySet()) {
            body.append("resource.")
                    .append(b64(entry.getKey()))
                    .append('=')
                    .append(b64(entry.getValue()))
                    .append('\n');
        }
        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(state.references()).entrySet()) {
            for (String target : new TreeSet<>(entry.getValue())) {
                body.append("reference=")
                        .append(b64(entry.getKey()))
                        .append('|')
                        .append(b64(target))
                        .append('\n');
            }
        }
        for (String dependency : new TreeSet<>(state.dependencyRefs())) {
            body.append("dependency=").append(b64(dependency)).append('\n');
        }

        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if (bodyBytes.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("project payload exceeds codec budget");
        }
        return body.append("sha256=")
                .append(DigestUtils.sha256(bodyBytes))
                .append('\n')
                .toString();
    }

    public ProjectState decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("project payload missing");
        }
        byte[] encodedBytes = encoded.getBytes(StandardCharsets.UTF_8);
        if (encodedBytes.length == 0 || encodedBytes.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("project payload size invalid");
        }
        if (!encoded.endsWith("\n")) {
            throw new IllegalArgumentException("project payload newline missing");
        }

        int checksumStart = encoded.lastIndexOf("sha256=");
        if (checksumStart <= 0 || encoded.indexOf("sha256=") != checksumStart) {
            throw new IllegalArgumentException("project checksum missing or duplicated");
        }
        String body = encoded.substring(0, checksumStart);
        String checksumLine = encoded.substring(checksumStart, encoded.length() - 1);
        String expected = checksumLine.substring("sha256=".length());
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("project checksum format invalid");
        }
        String actual = DigestUtils.sha256(body.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("project checksum mismatch");
        }

        String[] lines = body.split("\n");
        if (lines.length < 8 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("project header invalid");
        }

        Map<String, String> meta = new LinkedHashMap<>();
        Map<String, String> resources = new TreeMap<>();
        Map<String, Set<String>> references = new TreeMap<>();
        Set<String> dependencies = new TreeSet<>();
        int actualReferences = 0;

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.startsWith("resource.")) {
                int separator = line.indexOf('=');
                if (separator <= "resource.".length()) {
                    throw new IllegalArgumentException("resource record invalid");
                }
                String id = text(line.substring("resource.".length(), separator));
                String payload = text(line.substring(separator + 1));
                if (resources.put(id, payload) != null) {
                    throw new IllegalArgumentException("duplicate resource");
                }
            } else if (line.startsWith("reference=")) {
                String pair = line.substring("reference=".length());
                int separator = pair.indexOf('|');
                if (separator <= 0 || separator == pair.length() - 1) {
                    throw new IllegalArgumentException("reference record invalid");
                }
                String source = text(pair.substring(0, separator));
                String target = text(pair.substring(separator + 1));
                if (!references.computeIfAbsent(source, ignored -> new LinkedHashSet<>())
                        .add(target)) {
                    throw new IllegalArgumentException("duplicate reference");
                }
                actualReferences++;
            } else if (line.startsWith("dependency=")) {
                if (!dependencies.add(text(line.substring("dependency=".length())))) {
                    throw new IllegalArgumentException("duplicate dependency");
                }
            } else {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("metadata record invalid");
                }
                String key = line.substring(0, separator);
                String value = line.substring(separator + 1);
                if (meta.put(key, value) != null) {
                    throw new IllegalArgumentException("duplicate metadata");
                }
            }
        }

        requireMetadata(meta, "projectId");
        requireMetadata(meta, "schemaVersion");
        requireMetadata(meta, "buildModelVersion");
        requireMetadata(meta, "revision");
        requireMetadata(meta, "lifecycle");
        requireMetadata(meta, "resourceCount");
        requireMetadata(meta, "referenceCount");
        requireMetadata(meta, "dependencyCount");
        if (meta.size() != 8) {
            throw new IllegalArgumentException("unknown project metadata");
        }

        int expectedResources = parseInt(meta.get("resourceCount"), "resourceCount");
        int expectedReferences = parseInt(meta.get("referenceCount"), "referenceCount");
        int expectedDependencies = parseInt(meta.get("dependencyCount"), "dependencyCount");
        if (expectedResources != resources.size()
                || expectedReferences != actualReferences
                || expectedDependencies != dependencies.size()) {
            throw new IllegalArgumentException("project count mismatch");
        }

        return ProjectState.restore(
                text(meta.get("projectId")),
                parseInt(meta.get("schemaVersion"), "schemaVersion"),
                parseInt(meta.get("buildModelVersion"), "buildModelVersion"),
                parseLong(meta.get("revision"), "revision"),
                parseLifecycle(meta.get("lifecycle")),
                resources,
                references,
                dependencies
        );
    }

    private static void requireMetadata(Map<String, String> meta, String key) {
        if (!meta.containsKey(key)) {
            throw new IllegalArgumentException("missing metadata: " + key);
        }
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid base64 field", error);
        }
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(field + " invalid", error);
        }
    }

    private static long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(field + " invalid", error);
        }
    }

    private static ProjectLifecycle parseLifecycle(String value) {
        try {
            return ProjectLifecycle.valueOf(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("lifecycle invalid", error);
        }
    }
}
