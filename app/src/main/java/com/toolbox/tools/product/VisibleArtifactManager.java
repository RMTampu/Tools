package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.library.AssetDependencyRef;
import com.toolbox.tools.library.DependencyRef;
import com.toolbox.tools.library.TemplateDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.TreeSet;

public final class VisibleArtifactManager {
    public static final class Record {
        private final VisibleWorkspaceStore.Area area;
        private final String fileName;
        private final long revision;
        private final long sizeBytes;
        private final String sha256;

        Record(
                VisibleWorkspaceStore.Area area,
                String fileName,
                long revision,
                long sizeBytes,
                String sha256
        ) {
            this.area = area;
            this.fileName = fileName;
            this.revision = revision;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }

        public VisibleWorkspaceStore.Area area() { return area; }
        public String fileName() { return fileName; }
        public long revision() { return revision; }
        public long sizeBytes() { return sizeBytes; }
        public String sha256() { return sha256; }
    }

    private final ProjectManager projects;
    private final VisibleWorkspaceStore visible;
    private final ProjectCodec codec = new ProjectCodec();

    public VisibleArtifactManager(
            ProjectManager projects,
            VisibleWorkspaceStore visible
    ) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.visible = Objects.requireNonNull(visible, "visible");
    }

    public synchronized Record snapshot(
            String label,
            ProjectState state
    ) throws IOException {
        Objects.requireNonNull(state, "state");
        String safeLabel = sanitizeLabel(label);
        String name = "snapshot-r"
                + state.revision()
                + "-"
                + System.currentTimeMillis()
                + "-"
                + safeLabel
                + ".tbx";
        byte[] bytes = codec.encode(state)
                .getBytes(StandardCharsets.UTF_8);
        VisibleWorkspaceStore.WriteResult result =
                visible.writeStream(
                        VisibleWorkspaceStore.Area.SNAPSHOTS,
                        name,
                        new java.io.ByteArrayInputStream(bytes),
                        16L * 1024L * 1024L
                );
        ProjectState verified = codec.decode(new String(
                visible.read(
                        VisibleWorkspaceStore.Area.SNAPSHOTS,
                        name
                ),
                StandardCharsets.UTF_8
        ));
        if (!state.equals(verified)) {
            throw new IOException("snapshot visible round-trip mismatch");
        }
        return new Record(
                VisibleWorkspaceStore.Area.SNAPSHOTS,
                name,
                state.revision(),
                result.bytesWritten(),
                result.sha256()
        );
    }

    public synchronized Record snapshotCurrent(String label)
            throws IOException {
        if (projects.hasUnsavedChanges()
                || projects.savedRevision() <= 0) {
            throw new IllegalStateException(
                    "snapshot visible requires clean saved project"
            );
        }
        return snapshot(label, projects.current());
    }

    public synchronized Record exportCurrent()
            throws IOException {
        if (projects.hasUnsavedChanges()
                || projects.savedRevision() <= 0) {
            throw new IllegalStateException(
                    "export requires clean saved project"
            );
        }

        ProjectState state = projects.current();
        rejectSensitiveProjectKeys(state);

        String prefix = "project-"
                + state.projectId().replace('.', '_')
                + "-r"
                + state.revision()
                + "-"
                + System.currentTimeMillis();

        String projectName = prefix + ".project.tbx";
        String manifestName = prefix + ".manifest";
        List<String> created = new ArrayList<>();
        LinkedHashMap<String, String> hashes =
                new LinkedHashMap<>();
        LinkedHashMap<String, String> exportedAssets =
                new LinkedHashMap<>();

        try {
            byte[] projectBytes = codec.encode(state)
                    .getBytes(StandardCharsets.UTF_8);
            VisibleWorkspaceStore.WriteResult projectResult =
                    visible.writeStream(
                            VisibleWorkspaceStore.Area.EXPORTS,
                            projectName,
                            new java.io.ByteArrayInputStream(
                                    projectBytes
                            ),
                            32L * 1024L * 1024L
                    );
            created.add(projectName);
            hashes.put(projectName, projectResult.sha256());

            ProjectState verified = codec.decode(new String(
                    visible.read(
                            VisibleWorkspaceStore.Area.EXPORTS,
                            projectName
                    ),
                    StandardCharsets.UTF_8
            ));
            if (!state.equals(verified)) {
                throw new IOException(
                        "project export round-trip mismatch"
                );
            }

            int assetIndex = 0;
            for (Map.Entry<String, String> entry
                    : new TreeMap<>(
                            state.resources()
                    ).entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("asset.external.")
                        || !key.endsWith(".storage.name")) {
                    continue;
                }
                String assetId = key.substring(
                        0,
                        key.length() - ".storage.name".length()
                );
                String storageName = entry.getValue();
                String area = state.resources().get(
                        assetId + ".storage.area"
                );
                String expectedSha = state.resources().get(
                        assetId + ".sha256"
                );
                if (!VisibleWorkspaceStore.Area.ASSETS
                        .folder()
                        .equals(area)
                        || expectedSha == null
                        || !expectedSha.matches("[0-9a-f]{64}")
                        || !visible.exists(
                                VisibleWorkspaceStore.Area.ASSETS,
                                storageName
                        )) {
                    throw new IOException(
                            "EXPORT_REQUIRED_ASSET_MISSING:"
                                    + assetId
                    );
                }

                assetIndex++;
                String exportName = prefix
                        + "-asset-"
                        + String.format(
                                java.util.Locale.ROOT,
                                "%03d",
                                assetIndex
                        )
                        + "-"
                        + expectedSha.substring(0, 16)
                        + safeExtension(storageName);

                VisibleWorkspaceStore.WriteResult result;
                try (InputStream input =
                        visible.openInputStream(
                                VisibleWorkspaceStore.Area.ASSETS,
                                storageName
                        )) {
                    result = visible.writeStream(
                            VisibleWorkspaceStore.Area.EXPORTS,
                            exportName,
                            input,
                            128L * 1024L * 1024L
                    );
                }
                if (!expectedSha.equals(result.sha256())) {
                    throw new IOException(
                            "EXPORT_REQUIRED_ASSET_INTEGRITY_FAILED:"
                                    + assetId
                    );
                }
                created.add(exportName);
                hashes.put(exportName, result.sha256());
                exportedAssets.put(assetId, exportName);
            }

            String manifest = exportManifest(
                    state,
                    projectName,
                    hashes.get(projectName),
                    exportedAssets,
                    hashes
            );
            byte[] manifestBytes = manifest.getBytes(
                    StandardCharsets.UTF_8
            );
            VisibleWorkspaceStore.WriteResult manifestResult =
                    visible.writeStream(
                            VisibleWorkspaceStore.Area.EXPORTS,
                            manifestName,
                            new java.io.ByteArrayInputStream(
                                    manifestBytes
                            ),
                            2L * 1024L * 1024L
                    );
            created.add(manifestName);

            byte[] rereadManifest = visible.read(
                    VisibleWorkspaceStore.Area.EXPORTS,
                    manifestName
            );
            if (!java.security.MessageDigest.isEqual(
                    manifestBytes,
                    rereadManifest
            )) {
                throw new IOException(
                        "project export manifest reread mismatch"
                );
            }

            return new Record(
                    VisibleWorkspaceStore.Area.EXPORTS,
                    manifestName,
                    state.revision(),
                    manifestResult.bytesWritten(),
                    manifestResult.sha256()
            );
        } catch (IOException | RuntimeException error) {
            for (String name : created) {
                try {
                    visible.delete(
                            VisibleWorkspaceStore.Area.EXPORTS,
                            name
                    );
                } catch (IOException cleanupError) {
                    // A partial export is never returned as success; any
                    // undeletable residue remains explicit/user-visible.
                }
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(
                    "PROJECT_EXPORT_FAILED",
                    error
            );
        }
    }

    private static String exportManifest(
            ProjectState state,
            String projectName,
            String projectSha,
            Map<String, String> assets,
            Map<String, String> hashes
    ) {
        StringBuilder out = new StringBuilder();
        out.append("TBX_PROJECT_EXPORT_V2\n");
        out.append("PROJECT_ID=")
                .append(state.projectId())
                .append('\n');
        out.append("PROJECT_REVISION=")
                .append(state.revision())
                .append('\n');
        out.append("SCHEMA_VERSION=")
                .append(state.schemaVersion())
                .append('\n');
        out.append("BUILD_MODEL_VERSION=")
                .append(state.buildModelVersion())
                .append('\n');
        out.append("PROJECT_FILE=")
                .append(projectName)
                .append('|')
                .append(projectSha)
                .append('\n');

        for (String dependency
                : new TreeSet<>(state.dependencyRefs())) {
            out.append("DEPENDENCY=")
                    .append(dependency)
                    .append('\n');
        }
        for (Map.Entry<String, String> entry
                : new TreeMap<>(assets).entrySet()) {
            out.append("ASSET=")
                    .append(entry.getKey())
                    .append('|')
                    .append(entry.getValue())
                    .append('|')
                    .append(hashes.get(entry.getValue()))
                    .append('\n');
        }

        int screens = 0;
        int logic = 0;
        int data = 0;
        int bindings = 0;
        int styles = 0;
        int localization = 0;
        for (String key : state.resources().keySet()) {
            if (key.startsWith("ui.screen.")) screens++;
            else if (key.startsWith("logic.")) logic++;
            else if (key.startsWith("data.")) data++;
            else if (key.startsWith("binding.")) bindings++;
            else if (key.startsWith("style.")
                    || key.startsWith("token.")
                    || key.startsWith("theme.")) styles++;
            else if (key.startsWith("localization.")
                    || key.startsWith("i18n.")) localization++;
        }
        out.append("SECTION_COUNT=screens|")
                .append(screens).append('\n');
        out.append("SECTION_COUNT=logic|")
                .append(logic).append('\n');
        out.append("SECTION_COUNT=data|")
                .append(data).append('\n');
        out.append("SECTION_COUNT=bindings|")
                .append(bindings).append('\n');
        out.append("SECTION_COUNT=styles|")
                .append(styles).append('\n');
        out.append("SECTION_COUNT=localization|")
                .append(localization).append('\n');
        out.append("CACHE_INCLUDED=NO\n");
        out.append("UNDO_HISTORY_INCLUDED=NO\n");
        out.append("PREVIEW_INCLUDED=NO\n");
        out.append("RECOVERY_JOURNAL_INCLUDED=NO\n");
        out.append("SECRET_INCLUDED=NO\n");
        return out.toString();
    }

    private static void rejectSensitiveProjectKeys(
            ProjectState state
    ) throws IOException {
        for (String key : state.resources().keySet()) {
            String normalized = key.toLowerCase(
                    java.util.Locale.ROOT
            );
            if (normalized.contains("private.key")
                    || normalized.contains("private_key")
                    || normalized.contains("keystore.password")
                    || normalized.contains("keystore_password")
                    || normalized.contains("api.secret")
                    || normalized.contains("api_secret")
                    || normalized.contains("access.token")
                    || normalized.contains("access_token")
                    || normalized.contains("github.token")
                    || normalized.contains("github_token")) {
                throw new IOException(
                        "EXPORT_SECRET_KEY_BLOCKED:" + key
                );
            }
        }
    }

    private static String safeExtension(String storageName) {
        if (storageName == null) return ".bin";
        int dot = storageName.lastIndexOf('.');
        if (dot < 0 || dot == storageName.length() - 1) {
            return ".bin";
        }
        String extension = storageName.substring(dot)
                .toLowerCase(java.util.Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,8}")
                ? extension
                : ".bin";
    }

    public synchronized Record publishTemplate(
            TemplateDefinition template
    ) throws IOException {
        Objects.requireNonNull(template, "template");
        StringBuilder out = new StringBuilder();
        out.append("TBX_TEMPLATE_V1\n");
        out.append("id=").append(template.templateId()).append('\n');
        out.append("label=").append(escape(template.labelIndonesia())).append('\n');
        out.append("version=").append(template.version()).append('\n');
        out.append("lifecycle=").append(template.lifecycle().name()).append('\n');

        for (String id : new TreeSet<>(template.internalObjectIds())) {
            out.append("object=").append(id).append('\n');
        }

        List<String> componentLines = new ArrayList<>();
        for (DependencyRef dependency : template.componentDependencies()) {
            componentLines.add(
                    dependency.dependencyId()
                            + "@"
                            + dependency.versionRange()
            );
        }
        Collections.sort(componentLines);
        for (String value : componentLines) {
            out.append("component=").append(value).append('\n');
        }

        List<String> assetLines = new ArrayList<>();
        for (AssetDependencyRef dependency : template.assetDependencies()) {
            assetLines.add(
                    dependency.assetId()
                            + "@"
                            + dependency.versionRange()
            );
        }
        Collections.sort(assetLines);
        for (String value : assetLines) {
            out.append("asset=").append(value).append('\n');
        }

        byte[] bytes = out.toString()
                .getBytes(StandardCharsets.UTF_8);
        String name = template.templateId()
                .replace('.', '_')
                + "-"
                + template.version().toString()
                .replace('.', '_')
                + ".tbxt";
        VisibleWorkspaceStore.WriteResult result =
                visible.writeStream(
                        VisibleWorkspaceStore.Area.TEMPLATES,
                        name,
                        new java.io.ByteArrayInputStream(bytes),
                        2L * 1024L * 1024L
                );
        if (!java.util.Arrays.equals(
                bytes,
                visible.read(
                        VisibleWorkspaceStore.Area.TEMPLATES,
                        name
                )
        )) {
            throw new IOException("template visible verification mismatch");
        }
        return new Record(
                VisibleWorkspaceStore.Area.TEMPLATES,
                name,
                projects.savedRevision(),
                result.bytesWritten(),
                result.sha256()
        );
    }

    public synchronized List<String> list(
            VisibleWorkspaceStore.Area area
    ) throws IOException {
        return visible.list(area);
    }

    private static String sanitizeLabel(String value) {
        String raw = value == null ? "checkpoint" : value.trim();
        if (raw.isEmpty()) raw = "checkpoint";
        String safe = raw.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.isEmpty()) safe = "checkpoint";
        return safe.length() > 40
                ? safe.substring(0, 40)
                : safe;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("=", "\\=");
    }
}
