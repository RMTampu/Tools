package com.toolbox.tools.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ProjectDefinitionCodec {
    private static final String HEADER = "TBX_PROJECT_DEFINITION_V1";

    public String encode(ProjectState state) {
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

        for (String id : new TreeSet<>(state.resources().keySet())) {
            body.append("resourceId=").append(b64(id)).append('\n');
        }
        for (Map.Entry<String, Set<String>> entry : state.references().entrySet()) {
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

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        return body.append("sha256=").append(DigestUtils.sha256(bytes)).append('\n').toString();
    }

    public ProjectState decode(String encoded, Map<String, String> resources) {
        if (encoded == null || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException("project definition invalid");
        }
        int checksumStart = encoded.lastIndexOf("sha256=");
        if (checksumStart <= 0 || encoded.indexOf("sha256=") != checksumStart) {
            throw new IllegalArgumentException("definition checksum missing");
        }
        String body = encoded.substring(0, checksumStart);
        String expected = encoded.substring(
                checksumStart + "sha256=".length(),
                encoded.length() - 1
        );
        String actual = DigestUtils.sha256(body.getBytes(StandardCharsets.UTF_8));
        if (!expected.matches("[0-9a-f]{64}") || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("definition checksum mismatch");
        }

        String[] lines = body.split("\n");
        if (lines.length < 8 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("definition header invalid");
        }

        Map<String, String> meta = new LinkedHashMap<>();
        Set<String> resourceIds = new TreeSet<>();
        Map<String, Set<String>> references = new LinkedHashMap<>();
        Set<String> dependencies = new TreeSet<>();
        int actualReferences = 0;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("resourceId=")) {
                if (!resourceIds.add(text(line.substring("resourceId=".length())))) {
                    throw new IllegalArgumentException("duplicate resource id");
                }
            } else if (line.startsWith("reference=")) {
                String pair = line.substring("reference=".length());
                int separator = pair.indexOf('|');
                if (separator <= 0 || separator == pair.length() - 1) {
                    throw new IllegalArgumentException("reference invalid");
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
                    throw new IllegalArgumentException("definition metadata invalid");
                }
                String key = line.substring(0, separator);
                String value = line.substring(separator + 1);
                if (meta.put(key, value) != null) {
                    throw new IllegalArgumentException("duplicate metadata");
                }
            }
        }

        String projectId = text(required(meta, "projectId"));
        int schema = integer(required(meta, "schemaVersion"));
        int buildModel = integer(required(meta, "buildModelVersion"));
        long revision = longValue(required(meta, "revision"));
        ProjectLifecycle lifecycle = ProjectLifecycle.valueOf(required(meta, "lifecycle"));
        int resourceCount = integer(required(meta, "resourceCount"));
        int referenceCount = integer(required(meta, "referenceCount"));
        int dependencyCount = integer(required(meta, "dependencyCount"));

        if (meta.size() != 8
                || resourceCount != resourceIds.size()
                || referenceCount != actualReferences
                || dependencyCount != dependencies.size()
                || !resourceIds.equals(new TreeSet<>(resources.keySet()))) {
            throw new IllegalArgumentException("definition count/resource mismatch");
        }

        return ProjectState.restore(
                projectId,
                schema,
                buildModel,
                revision,
                lifecycle,
                resources,
                references,
                dependencies
        );
    }

    public static String resourceFileName(String stableId) {
        return b64(StableId.require(stableId, "resourceId")) + ".res";
    }

    private static String required(Map<String, String> meta, String key) {
        String value = meta.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing definition metadata: " + key);
        }
        return value;
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid definition base64", error);
        }
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid definition integer", error);
        }
    }

    private static long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid definition long", error);
        }
    }
}
