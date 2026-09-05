package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.StableId;
import com.toolbox.tools.core.VisibleWorkspaceStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class ProjectAssetAudit {
    public enum Code {
        UNUSED_ASSET,
        MISSING_ASSET,
        BROKEN_ASSET_REFERENCE,
        DUPLICATE_CANDIDATE
    }

    public static final class Issue {
        private final Code code;
        private final String assetId;
        private final String relatedAssetId;
        private final String detail;

        Issue(
                Code code,
                String assetId,
                String relatedAssetId,
                String detail
        ) {
            this.code = Objects.requireNonNull(code, "code");
            this.assetId = StableId.require(assetId, "assetId");
            this.relatedAssetId = relatedAssetId == null
                    ? null
                    : StableId.require(
                            relatedAssetId,
                            "relatedAssetId"
                    );
            this.detail = detail == null ? "" : detail;
        }

        public Code code() { return code; }
        public String assetId() { return assetId; }
        public String relatedAssetId() { return relatedAssetId; }
        public String detail() { return detail; }
    }

    public static final class Report {
        private final List<Issue> issues;
        private final Set<String> referencedAssetIds;
        private final Set<String> definedExternalAssetIds;

        Report(
                List<Issue> issues,
                Set<String> referencedAssetIds,
                Set<String> definedExternalAssetIds
        ) {
            this.issues = Collections.unmodifiableList(
                    new ArrayList<>(issues)
            );
            this.referencedAssetIds = Collections.unmodifiableSet(
                    new LinkedHashSet<>(referencedAssetIds)
            );
            this.definedExternalAssetIds =
                    Collections.unmodifiableSet(
                            new LinkedHashSet<>(
                                    definedExternalAssetIds
                            )
                    );
        }

        public List<Issue> issues() { return issues; }
        public Set<String> referencedAssetIds() {
            return referencedAssetIds;
        }
        public Set<String> definedExternalAssetIds() {
            return definedExternalAssetIds;
        }

        public List<Issue> byCode(Code code) {
            List<Issue> out = new ArrayList<>();
            for (Issue issue : issues) {
                if (issue.code() == code) out.add(issue);
            }
            return Collections.unmodifiableList(out);
        }

        public boolean hasBlocking() {
            return !byCode(Code.MISSING_ASSET).isEmpty()
                    || !byCode(
                            Code.BROKEN_ASSET_REFERENCE
                    ).isEmpty();
        }
    }

    private final VisibleWorkspaceStore visible;
    private final AssetIntegrityVerifier integrity;
    private final AssetLoadManager registry;

    public ProjectAssetAudit(
            VisibleWorkspaceStore visible,
            AssetIntegrityVerifier integrity,
            AssetLoadManager registry
    ) {
        this.visible = Objects.requireNonNull(visible, "visible");
        this.integrity = Objects.requireNonNull(
                integrity,
                "integrity"
        );
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public synchronized Report scan(ProjectState project) {
        Objects.requireNonNull(project, "project");

        LinkedHashSet<String> builtin = new LinkedHashSet<>();
        for (AssetLoadManager.Descriptor descriptor
                : registry.all()) {
            builtin.add(descriptor.id());
        }

        LinkedHashMap<String, ExternalAsset> external =
                externalAssets(project);
        LinkedHashSet<String> references =
                referencedAssets(project);

        List<Issue> issues = new ArrayList<>();

        for (String reference : references) {
            if (!external.containsKey(reference)
                    && !builtin.contains(reference)) {
                issues.add(new Issue(
                        Code.BROKEN_ASSET_REFERENCE,
                        reference,
                        null,
                        "Stable Asset ID tidak mempunyai definisi."
                ));
            }
        }

        for (ExternalAsset asset : external.values()) {
            if (!references.contains(asset.id)) {
                issues.add(new Issue(
                        Code.UNUSED_ASSET,
                        asset.id,
                        null,
                        "Aset tidak direferensikan object/project."
                ));
            }

            if (!asset.metadataComplete()) {
                issues.add(new Issue(
                        Code.MISSING_ASSET,
                        asset.id,
                        null,
                        "Metadata storage/hash aset tidak lengkap."
                ));
                continue;
            }

            try {
                if (!visible.exists(
                        VisibleWorkspaceStore.Area.ASSETS,
                        asset.storageName
                )
                        || !integrity.verify(
                                visible,
                                VisibleWorkspaceStore.Area.ASSETS,
                                asset.storageName,
                                asset.sha256
                        )) {
                    issues.add(new Issue(
                            Code.MISSING_ASSET,
                            asset.id,
                            null,
                            "File hilang atau hash berbeda."
                    ));
                }
            } catch (IOException | RuntimeException error) {
                issues.add(new Issue(
                        Code.MISSING_ASSET,
                        asset.id,
                        null,
                        "File tidak dapat diverifikasi."
                ));
            }
        }

        Map<String, String> digestOwner =
                new LinkedHashMap<>();
        for (ExternalAsset asset : external.values()) {
            if (asset.sha256 == null
                    || !asset.sha256.matches("[0-9a-f]{64}")) {
                continue;
            }
            String previous = digestOwner.put(
                    asset.sha256,
                    asset.id
            );
            if (previous != null
                    && !previous.equals(asset.id)) {
                issues.add(new Issue(
                        Code.DUPLICATE_CANDIDATE,
                        asset.id,
                        previous,
                        "Content SHA-256 identik; tidak dihapus otomatis."
                ));
            }
        }

        return new Report(
                issues,
                references,
                external.keySet()
        );
    }

    public synchronized void relinkExternal(
            ProjectManager projects,
            String assetId,
            String storageName,
            String expectedSha256
    ) throws IOException {
        Objects.requireNonNull(projects, "projects");
        String id = StableId.require(assetId, "assetId");
        if (!id.startsWith("asset.external.")) {
            throw new IllegalArgumentException(
                    "relink hanya untuk external asset"
            );
        }
        if (storageName == null
                || storageName.trim().isEmpty()
                || expectedSha256 == null
                || !expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "relink metadata invalid"
            );
        }
        if (!visible.exists(
                VisibleWorkspaceStore.Area.ASSETS,
                storageName
        )
                || !integrity.verify(
                        visible,
                        VisibleWorkspaceStore.Area.ASSETS,
                        storageName,
                        expectedSha256
                )) {
            throw new IOException(
                    "relink target asset gagal integrity"
            );
        }

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put(
                id + ".storage.area",
                VisibleWorkspaceStore.Area.ASSETS.folder()
        );
        updates.put(id + ".storage.name", storageName);
        updates.put(id + ".sha256", expectedSha256);
        projects.applyResourceTransaction(
                updates,
                Collections.emptySet()
        );
    }

    private static LinkedHashMap<String, ExternalAsset>
            externalAssets(ProjectState project) {
        LinkedHashMap<String, ExternalAsset> out =
                new LinkedHashMap<>();
        Map<String, String> resources = project.resources();

        for (Map.Entry<String, String> entry
                : new TreeMap<>(resources).entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("asset.external.")
                    || !key.endsWith(".storage.name")) {
                continue;
            }
            String id = key.substring(
                    0,
                    key.length() - ".storage.name".length()
            );
            StableId.require(id, "assetId");
            out.put(
                    id,
                    new ExternalAsset(
                            id,
                            resources.get(
                                    id + ".storage.area"
                            ),
                            entry.getValue(),
                            resources.get(id + ".sha256")
                    )
            );
        }

        // A partially-declared external asset still counts as a definition
        // so the audit can report MISSING_ASSET instead of silently omitting it.
        for (String key : resources.keySet()) {
            if (!key.startsWith("asset.external.")) continue;
            for (String suffix : new String[] {
                    ".storage.area",
                    ".sha256",
                    ".kind",
                    ".mime",
                    ".name"
            }) {
                if (!key.endsWith(suffix)) continue;
                String id = key.substring(
                        0,
                        key.length() - suffix.length()
                );
                if (!out.containsKey(id)) {
                    out.put(
                            id,
                            new ExternalAsset(
                                    id,
                                    resources.get(
                                            id + ".storage.area"
                                    ),
                                    resources.get(
                                            id + ".storage.name"
                                    ),
                                    resources.get(id + ".sha256")
                            )
                    );
                }
            }
        }
        return out;
    }

    private static LinkedHashSet<String> referencedAssets(
            ProjectState project
    ) {
        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        for (Set<String> targets
                : project.references().values()) {
            for (String target : targets) {
                if (target.startsWith("asset.")) {
                    out.add(
                            StableId.require(
                                    target,
                                    "assetReference"
                            )
                    );
                }
            }
        }

        for (Map.Entry<String, String> entry
                : project.resources().entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith(".asset.id")) continue;
            String value = entry.getValue();
            if (value != null && value.startsWith("asset.")) {
                out.add(
                        StableId.require(
                                value,
                                "assetReference"
                        )
                );
            }
        }
        return out;
    }

    private static final class ExternalAsset {
        final String id;
        final String area;
        final String storageName;
        final String sha256;

        ExternalAsset(
                String id,
                String area,
                String storageName,
                String sha256
        ) {
            this.id = id;
            this.area = area;
            this.storageName = storageName;
            this.sha256 = sha256;
        }

        boolean metadataComplete() {
            return VisibleWorkspaceStore.Area.ASSETS
                    .folder()
                    .equals(area)
                    && storageName != null
                    && !storageName.trim().isEmpty()
                    && sha256 != null
                    && sha256.matches("[0-9a-f]{64}");
        }
    }
}
