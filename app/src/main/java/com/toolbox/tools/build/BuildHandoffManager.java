package com.toolbox.tools.build;

import com.toolbox.tools.core.ConfigStore;
import com.toolbox.tools.core.DigestUtils;
import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.VisibleWorkspaceStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class BuildHandoffManager {
    private static final long MAX_PROJECT_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_ASSET_BYTES = 128L * 1024L * 1024L;

    private final ProjectManager projects;
    private final ReadyCoordinator ready;
    private final VisibleWorkspaceStore visible;
    private final ConfigStore config;
    private final ProjectCodec codec = new ProjectCodec();

    public BuildHandoffManager(
            ProjectManager projects,
            ReadyCoordinator ready,
            VisibleWorkspaceStore visible,
            ConfigStore config
    ) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.ready = Objects.requireNonNull(ready, "ready");
        this.visible = Objects.requireNonNull(visible, "visible");
        this.config = Objects.requireNonNull(config, "config");
    }

    public synchronized BuildHandoffPackage prepare(
            BuildProvenance provenance
    ) throws IOException {
        Objects.requireNonNull(provenance, "provenance");
        if (!provenance.exactSourceBound()) {
            throw new IOException(
                    "BUILD_SOURCE_PROVENANCE_UNBOUND"
            );
        }

        ProjectState published = ready.publishReady();
        ApplicationIr ir = ready.buildIr();
        if (ir.projectRevision() != published.revision()) {
            throw new IOException("BUILD_IR_REVISION_MISMATCH");
        }

        byte[] projectBytes = codec.encode(published)
                .getBytes(StandardCharsets.UTF_8);
        byte[] irBytes = ir.canonical()
                .getBytes(StandardCharsets.UTF_8);
        if (projectBytes.length > MAX_PROJECT_BYTES) {
            throw new IOException("BUILD_PROJECT_PACKAGE_TOO_LARGE");
        }

        TreeMap<String, AssetSource> assets =
                collectExternalAssets(published);
        String baseManifest = canonicalManifest(
                published,
                ir,
                projectBytes,
                assets,
                provenance
        );
        String buildId = DigestUtils.sha256(
                baseManifest.getBytes(StandardCharsets.UTF_8)
        );
        String manifest = baseManifest
                + "BUILD_ID=" + buildId + "\n"
                + "STATUS=IMMUTABLE_READY_TO_BUILD\n";
        byte[] manifestBytes = manifest.getBytes(
                StandardCharsets.UTF_8
        );

        String prefix = "build-" + buildId.substring(0, 24);
        String manifestName = prefix + ".manifest";
        String irName = prefix + ".ir";
        String projectName = prefix + ".project.tbx";

        List<String> created = new ArrayList<>();
        LinkedHashMap<String, String> exportedAssets =
                new LinkedHashMap<>();
        LinkedHashMap<String, String> contentHashes =
                new LinkedHashMap<>();

        try {
            writeVerified(
                    manifestName,
                    manifestBytes,
                    created,
                    contentHashes
            );
            writeVerified(
                    irName,
                    irBytes,
                    created,
                    contentHashes
            );
            writeVerified(
                    projectName,
                    projectBytes,
                    created,
                    contentHashes
            );

            int index = 0;
            for (Map.Entry<String, AssetSource> entry
                    : assets.entrySet()) {
                index++;
                AssetSource source = entry.getValue();
                String exportName = prefix
                        + "-asset-"
                        + String.format(
                                Locale.ROOT,
                                "%03d",
                                index
                        )
                        + "-"
                        + source.sha256.substring(0, 16)
                        + extension(source.storageName);
                try (InputStream input = visible.openInputStream(
                        VisibleWorkspaceStore.Area.ASSETS,
                        source.storageName
                )) {
                    VisibleWorkspaceStore.WriteResult result =
                            visible.writeStream(
                                    VisibleWorkspaceStore.Area.EXPORTS,
                                    exportName,
                                    input,
                                    MAX_ASSET_BYTES
                            );
                    if (!source.sha256.equals(result.sha256())
                            || source.sizeBytes
                                != result.bytesWritten()) {
                        throw new IOException(
                                "BUILD_ASSET_EXPORT_MISMATCH:"
                                        + entry.getKey()
                        );
                    }
                    created.add(exportName);
                    exportedAssets.put(
                            entry.getKey(),
                            exportName
                    );
                    contentHashes.put(
                            exportName,
                            result.sha256()
                    );
                }
            }

            String packageContentSha = packageContentSha(
                    contentHashes
            );
            return new BuildHandoffPackage(
                    buildId,
                    published.revision(),
                    manifestName,
                    irName,
                    projectName,
                    exportedAssets,
                    packageContentSha
            );
        } catch (IOException | RuntimeException error) {
            for (String name : created) {
                try {
                    visible.delete(
                            VisibleWorkspaceStore.Area.EXPORTS,
                            name
                    );
                } catch (IOException ignored) {
                    // Partial handoff remains visible and detectable;
                    // no build ID is returned as successful.
                }
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(
                    "BUILD_HANDOFF_FAILED",
                    error
            );
        }
    }

    public synchronized boolean contractReady(
            BuildProvenance provenance
    ) {
        return provenance != null
                && provenance.exactSourceBound()
                && "30".equals(
                    config.get("targetApi", "")
                )
                && "arm64".equals(
                    config.get("targetAbi", "")
                );
    }

    private void writeVerified(
            String name,
            byte[] bytes,
            List<String> created,
            Map<String, String> contentHashes
    ) throws IOException {
        VisibleWorkspaceStore.WriteResult result =
                visible.writeStream(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        name,
                        new ByteArrayInputStream(bytes),
                        Math.max(1024L, bytes.length + 1L)
                );
        String expected = DigestUtils.sha256(bytes);
        if (!expected.equals(result.sha256())
                || result.bytesWritten() != bytes.length) {
            throw new IOException(
                    "BUILD_HANDOFF_WRITE_VERIFICATION_FAILED:"
                            + name
            );
        }
        byte[] reread = visible.read(
                VisibleWorkspaceStore.Area.EXPORTS,
                name
        );
        if (!MessageDigest.isEqual(bytes, reread)) {
            throw new IOException(
                    "BUILD_HANDOFF_REREAD_MISMATCH:"
                            + name
            );
        }
        created.add(name);
        contentHashes.put(name, expected);
    }

    private TreeMap<String, AssetSource> collectExternalAssets(
            ProjectState project
    ) throws IOException {
        TreeMap<String, AssetSource> out = new TreeMap<>();
        for (Map.Entry<String, String> entry
                : project.resources().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("asset.external.")
                    || !key.endsWith(".storage.name")) {
                continue;
            }
            String assetId = key.substring(
                    0,
                    key.length() - ".storage.name".length()
            );
            String area = project.resources().get(
                    assetId + ".storage.area"
            );
            String sha = project.resources().get(
                    assetId + ".sha256"
            );
            String storageName = entry.getValue();
            if (!VisibleWorkspaceStore.Area.ASSETS.folder()
                    .equals(area)
                    || sha == null
                    || !sha.matches("[0-9a-f]{64}")
                    || !visible.exists(
                        VisibleWorkspaceStore.Area.ASSETS,
                        storageName
                    )) {
                throw new IOException(
                        "BUILD_REQUIRED_ASSET_MISSING:"
                                + assetId
                );
            }
            AssetDigest actual = digestVisibleAsset(storageName);
            if (!sha.equals(actual.sha256)) {
                throw new IOException(
                        "BUILD_REQUIRED_ASSET_INTEGRITY_FAILED:"
                                + assetId
                );
            }
            out.put(
                    assetId,
                    new AssetSource(
                            storageName,
                            sha,
                            actual.sizeBytes
                    )
            );
        }
        return out;
    }

    private AssetDigest digestVisibleAsset(String storageName)
            throws IOException {
        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance(
                    "SHA-256"
            );
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
        long total = 0;
        try (InputStream input = visible.openInputStream(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        )) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ASSET_BYTES) {
                    throw new IOException(
                            "BUILD_REQUIRED_ASSET_TOO_LARGE"
                    );
                }
                digest.update(buffer, 0, read);
            }
        }
        return new AssetDigest(
                total,
                hex(digest.digest())
        );
    }

    private String canonicalManifest(
            ProjectState project,
            ApplicationIr ir,
            byte[] projectBytes,
            Map<String, AssetSource> assets,
            BuildProvenance provenance
    ) {
        String applicationId = project.resources()
                .getOrDefault(
                        "build.application.id",
                        defaultApplicationId(project.projectId())
                );
        if (!applicationId.matches(
                "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"
        )) {
            throw new IllegalArgumentException(
                    "build.application.id invalid"
            );
        }

        int versionCode = versionCode(project);
        String versionName = project.resources().getOrDefault(
                "build.version.name",
                "1.0-r" + project.revision()
        );
        if (!versionName.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException(
                    "build.version.name invalid"
            );
        }

        StringBuilder out = new StringBuilder();
        out.append("TBX_BUILD_PACKAGE_V1\n");
        out.append("PROJECT_ID=")
                .append(project.projectId())
                .append('\n');
        out.append("PROJECT_REVISION=")
                .append(project.revision())
                .append('\n');
        out.append("SCHEMA_VERSION=")
                .append(project.schemaVersion())
                .append('\n');
        out.append("BUILD_MODEL_VERSION=")
                .append(project.buildModelVersion())
                .append('\n');
        out.append("APPLICATION_ID=")
                .append(applicationId)
                .append('\n');
        out.append("VERSION_CODE=")
                .append(versionCode)
                .append('\n');
        out.append("VERSION_NAME=")
                .append(versionName)
                .append('\n');
        out.append("TARGET_API=")
                .append(config.get("targetApi", ""))
                .append('\n');
        out.append("TARGET_ABI=")
                .append(config.get("targetAbi", ""))
                .append("-v8a\n");
        out.append("IR_VERSION=")
                .append(ir.irVersion())
                .append('\n');
        out.append("IR_SHA256=")
                .append(ir.sha256())
                .append('\n');
        out.append("PROJECT_SHA256=")
                .append(DigestUtils.sha256(projectBytes))
                .append('\n');
        out.append(provenance.canonical());

        for (String dependency
                : new TreeSet<>(project.dependencyRefs())) {
            out.append("DEPENDENCY=")
                    .append(dependency)
                    .append('\n');
        }
        for (Map.Entry<String, AssetSource> entry
                : assets.entrySet()) {
            out.append("ASSET=")
                    .append(entry.getKey())
                    .append('|')
                    .append(entry.getValue().storageName)
                    .append('|')
                    .append(entry.getValue().sizeBytes)
                    .append('|')
                    .append(entry.getValue().sha256)
                    .append('\n');
        }
        out.append("INCLUDE=application.ir\n");
        out.append("INCLUDE=project.tbx\n");
        out.append("SECRET_INCLUDED=NO\n");
        out.append("CACHE_INCLUDED=NO\n");
        out.append("UNDO_HISTORY_INCLUDED=NO\n");
        out.append("RECOVERY_INCLUDED=NO\n");
        return out.toString();
    }

    private static int versionCode(ProjectState project) {
        String configured = project.resources().get(
                "build.version.code"
        );
        if (configured == null) {
            return (int) Math.max(
                    1,
                    Math.min(
                            Integer.MAX_VALUE,
                            project.revision()
                    )
            );
        }
        try {
            int value = Integer.parseInt(configured);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "build.version.code invalid",
                    error
            );
        }
    }

    private static String defaultApplicationId(String projectId) {
        String normalized = projectId
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_");
        if (normalized.isEmpty()
                || !Character.isLetter(normalized.charAt(0))) {
            normalized = "project_" + normalized;
        }
        return "com.toolbox.generated." + normalized;
    }

    private static String extension(String storageName) {
        int dot = storageName.lastIndexOf('.');
        if (dot < 0 || dot == storageName.length() - 1) {
            return ".bin";
        }
        String value = storageName.substring(dot)
                .toLowerCase(Locale.ROOT);
        return value.matches("\\.[a-z0-9]{1,8}")
                ? value
                : ".bin";
    }

    private static String packageContentSha(
            Map<String, String> files
    ) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry
                : new TreeMap<>(files).entrySet()) {
            out.append(entry.getKey())
                    .append('|')
                    .append(entry.getValue())
                    .append('\n');
        }
        return DigestUtils.sha256(
                out.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format(
                    Locale.ROOT,
                    "%02x",
                    value
            ));
        }
        return out.toString();
    }

    private static final class AssetSource {
        final String storageName;
        final String sha256;
        final long sizeBytes;

        AssetSource(
                String storageName,
                String sha256,
                long sizeBytes
        ) {
            this.storageName = storageName;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }
    }

    private static final class AssetDigest {
        final long sizeBytes;
        final String sha256;

        AssetDigest(long sizeBytes, String sha256) {
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }
    }
}
