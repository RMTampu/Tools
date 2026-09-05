package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.library.AssetDependencyRef;
import com.toolbox.tools.library.DependencyRef;
import com.toolbox.tools.library.TemplateDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        String name = "project-"
                + state.projectId().replace('.', '_')
                + "-r"
                + state.revision()
                + ".tbx";
        byte[] bytes = codec.encode(state)
                .getBytes(StandardCharsets.UTF_8);
        VisibleWorkspaceStore.WriteResult result =
                visible.writeStream(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        name,
                        new java.io.ByteArrayInputStream(bytes),
                        32L * 1024L * 1024L
                );
        ProjectState verified = codec.decode(new String(
                visible.read(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        name
                ),
                StandardCharsets.UTF_8
        ));
        if (!state.equals(verified)) {
            throw new IOException("project export round-trip mismatch");
        }
        return new Record(
                VisibleWorkspaceStore.Area.EXPORTS,
                name,
                state.revision(),
                result.bytesWritten(),
                result.sha256()
        );
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
