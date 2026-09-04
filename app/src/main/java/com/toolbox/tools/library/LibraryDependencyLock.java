package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import com.toolbox.tools.core.StableId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class LibraryDependencyLock {
    public static final String PROJECT_RESOURCE_ID = "metadata.dependency.lock";
    private static final String HEADER = "TBX_DEPENDENCY_LOCK_V1";

    private final int projectSchemaVersion;
    private final int buildModelVersion;
    private final Map<String, VersionNumber> components;
    private final Map<String, VersionNumber> assets;
    private final Map<String, VersionNumber> adapters;

    public LibraryDependencyLock(
            int projectSchemaVersion,
            int buildModelVersion,
            Map<String, VersionNumber> components,
            Map<String, VersionNumber> assets,
            Map<String, VersionNumber> adapters
    ) {
        if (projectSchemaVersion <= 0 || buildModelVersion <= 0) {
            throw new IllegalArgumentException("lock model versions invalid");
        }
        this.projectSchemaVersion = projectSchemaVersion;
        this.buildModelVersion = buildModelVersion;
        this.components = immutableVersions(components, "componentId");
        this.assets = immutableVersions(assets, "assetId");
        this.adapters = immutableVersions(adapters, "adapterId");
    }

    public static LibraryDependencyLock empty(
            int projectSchemaVersion,
            int buildModelVersion
    ) {
        return new LibraryDependencyLock(
                projectSchemaVersion,
                buildModelVersion,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    public LibraryDependencyLock withComponent(
            String componentId,
            VersionNumber version
    ) {
        TreeMap<String, VersionNumber> next = new TreeMap<>(components);
        next.put(StableId.require(componentId, "componentId"), version);
        return new LibraryDependencyLock(
                projectSchemaVersion,
                buildModelVersion,
                next,
                assets,
                adapters
        );
    }

    public LibraryDependencyLock withAsset(
            String assetId,
            VersionNumber version
    ) {
        TreeMap<String, VersionNumber> next = new TreeMap<>(assets);
        next.put(StableId.require(assetId, "assetId"), version);
        return new LibraryDependencyLock(
                projectSchemaVersion,
                buildModelVersion,
                components,
                next,
                adapters
        );
    }

    public LibraryDependencyLock withAdapter(
            String adapterId,
            VersionNumber version
    ) {
        TreeMap<String, VersionNumber> next = new TreeMap<>(adapters);
        next.put(StableId.require(adapterId, "adapterId"), version);
        return new LibraryDependencyLock(
                projectSchemaVersion,
                buildModelVersion,
                components,
                assets,
                next
        );
    }

    public String encode() {
        StringBuilder body = new StringBuilder();
        body.append(HEADER).append('\n');
        body.append("projectSchemaVersion=")
                .append(projectSchemaVersion)
                .append('\n');
        body.append("buildModelVersion=")
                .append(buildModelVersion)
                .append('\n');
        for (Map.Entry<String, VersionNumber> entry : components.entrySet()) {
            body.append("component.")
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append('\n');
        }
        for (Map.Entry<String, VersionNumber> entry : assets.entrySet()) {
            body.append("asset.")
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append('\n');
        }
        for (Map.Entry<String, VersionNumber> entry : adapters.entrySet()) {
            body.append("adapter.")
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append('\n');
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        return body.append("sha256=")
                .append(DigestUtils.sha256(bytes))
                .append('\n')
                .toString();
    }

    public static LibraryDependencyLock decode(String encoded) {
        if (encoded == null || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException("dependency.lock invalid");
        }
        int checksumStart = encoded.lastIndexOf("sha256=");
        if (checksumStart <= 0 || encoded.indexOf("sha256=") != checksumStart) {
            throw new IllegalArgumentException("dependency.lock checksum missing");
        }
        String body = encoded.substring(0, checksumStart);
        String expected = encoded.substring(
                checksumStart + "sha256=".length(),
                encoded.length() - 1
        );
        String actual = DigestUtils.sha256(body.getBytes(StandardCharsets.UTF_8));
        if (!expected.matches("[0-9a-f]{64}")
                || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("dependency.lock checksum mismatch");
        }

        String[] lines = body.split("\n");
        if (lines.length < 3 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("dependency.lock header invalid");
        }

        Integer schema = null;
        Integer build = null;
        TreeMap<String, VersionNumber> components = new TreeMap<>();
        TreeMap<String, VersionNumber> assets = new TreeMap<>();
        TreeMap<String, VersionNumber> adapters = new TreeMap<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int equals = line.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("dependency.lock record invalid");
            }
            String key = line.substring(0, equals);
            String value = line.substring(equals + 1);
            if ("projectSchemaVersion".equals(key)) {
                if (schema != null) throw new IllegalArgumentException("duplicate schema");
                schema = integer(value);
            } else if ("buildModelVersion".equals(key)) {
                if (build != null) throw new IllegalArgumentException("duplicate build model");
                build = integer(value);
            } else if (key.startsWith("component.")) {
                putVersion(components, key.substring("component.".length()), value);
            } else if (key.startsWith("asset.")) {
                putVersion(assets, key.substring("asset.".length()), value);
            } else if (key.startsWith("adapter.")) {
                putVersion(adapters, key.substring("adapter.".length()), value);
            } else {
                throw new IllegalArgumentException("unknown dependency.lock field");
            }
        }

        if (schema == null || build == null) {
            throw new IllegalArgumentException("dependency.lock metadata incomplete");
        }
        return new LibraryDependencyLock(
                schema,
                build,
                components,
                assets,
                adapters
        );
    }

    public boolean resolves(
            ComponentRegistry componentRegistry,
            AssetRegistry assetRegistry
    ) {
        for (Map.Entry<String, VersionNumber> entry : components.entrySet()) {
            ComponentDefinition item = componentRegistry.resolveExact(
                    entry.getKey(),
                    entry.getValue()
            );
            if (item == null || item.lifecycle() != CatalogLifecycle.READY) return false;
        }
        for (Map.Entry<String, VersionNumber> entry : assets.entrySet()) {
            AssetDescriptor item = assetRegistry.resolveExact(
                    entry.getKey(),
                    entry.getValue()
            );
            if (item == null || item.lifecycle() != CatalogLifecycle.READY) return false;
        }
        return true;
    }

    public int projectSchemaVersion() { return projectSchemaVersion; }
    public int buildModelVersion() { return buildModelVersion; }
    public Map<String, VersionNumber> components() { return components; }
    public Map<String, VersionNumber> assets() { return assets; }
    public Map<String, VersionNumber> adapters() { return adapters; }

    private static Map<String, VersionNumber> immutableVersions(
            Map<String, VersionNumber> input,
            String field
    ) {
        TreeMap<String, VersionNumber> out = new TreeMap<>();
        if (input != null) {
            for (Map.Entry<String, VersionNumber> entry : input.entrySet()) {
                String id = StableId.require(entry.getKey(), field);
                if (out.put(id, java.util.Objects.requireNonNull(
                        entry.getValue(),
                        "version"
                )) != null) {
                    throw new IllegalArgumentException("duplicate dependency lock id");
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static void putVersion(
            Map<String, VersionNumber> map,
            String id,
            String value
    ) {
        String stable = StableId.require(id, "dependencyId");
        if (map.put(stable, VersionNumber.parse(value)) != null) {
            throw new IllegalArgumentException("duplicate dependency lock entry");
        }
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("dependency.lock integer invalid", error);
        }
    }
}
