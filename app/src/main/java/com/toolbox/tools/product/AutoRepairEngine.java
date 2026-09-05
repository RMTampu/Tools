package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class AutoRepairEngine {
    public enum RepairType {
        REBUILD_DERIVED_INDEX,
        REBUILD_DEPENDENCY_GRAPH,
        CLEAR_DISPOSABLE_CACHE,
        REMAP_EXACT_ID_CONFLICT,
        RELINK_EXACT_STABLE_ID,
        REGENERATE_DERIVED_MANIFEST
    }

    public static final class RepairResult {
        private final List<RepairType> applied;
        private final List<String> rejected;

        RepairResult(
                List<RepairType> applied,
                List<String> rejected
        ) {
            this.applied = Collections.unmodifiableList(
                    new ArrayList<>(applied)
            );
            this.rejected = Collections.unmodifiableList(
                    new ArrayList<>(rejected)
            );
        }

        public List<RepairType> applied() { return applied; }
        public List<String> rejected() { return rejected; }
        public boolean isPass() { return rejected.isEmpty(); }
    }

    private final ProjectManager projects;
    private final ProjectGraphManager graph;
    private final CacheManager cache;
    private String lastDerivedManifestSha256;

    public AutoRepairEngine() {
        this(null, null, null);
    }

    public AutoRepairEngine(
            ProjectManager projects,
            ProjectGraphManager graph,
            CacheManager cache
    ) {
        this.projects = projects;
        this.graph = graph;
        this.cache = cache;
    }

    public synchronized RepairResult applyDeterministic(
            List<RepairType> requested
    ) {
        List<RepairType> applied = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        if (requested == null) {
            return new RepairResult(applied, rejected);
        }

        for (RepairType type : requested) {
            if (type == null) {
                rejected.add("REPAIR_TYPE_NULL");
                continue;
            }

            if (requiresExactInput(type)) {
                rejected.add(
                        type.name() + ":EXACT_INPUT_REQUIRED"
                );
                continue;
            }

            if (projects == null
                    || graph == null
                    || cache == null) {
                rejected.add(
                        type.name() + ":REPAIR_RUNTIME_UNAVAILABLE"
                );
                continue;
            }

            try {
                switch (type) {
                    case REBUILD_DERIVED_INDEX:
                    case REBUILD_DEPENDENCY_GRAPH:
                        graph.rebuildFrom(projects.current());
                        applied.add(type);
                        break;
                    case CLEAR_DISPOSABLE_CACHE:
                        cache.clearDisposable();
                        applied.add(type);
                        break;
                    case REGENERATE_DERIVED_MANIFEST:
                        lastDerivedManifestSha256 =
                                regenerateDerivedManifest(
                                        projects.current()
                                );
                        applied.add(type);
                        break;
                    default:
                        rejected.add(
                                type.name() + ":UNSUPPORTED"
                        );
                        break;
                }
            } catch (RuntimeException error) {
                rejected.add(
                        type.name()
                                + ":"
                                + (error.getMessage() == null
                                    ? "FAILED"
                                    : error.getMessage())
                );
            }
        }
        return new RepairResult(applied, rejected);
    }

    public synchronized String lastDerivedManifestSha256() {
        return lastDerivedManifestSha256;
    }

    public boolean mayGuessBusinessLogic() { return false; }
    public boolean mayDeleteUserData() { return false; }

    private static boolean requiresExactInput(RepairType type) {
        return type == RepairType.REMAP_EXACT_ID_CONFLICT
                || type == RepairType.RELINK_EXACT_STABLE_ID;
    }

    private static String regenerateDerivedManifest(
            ProjectState project
    ) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("TBX_DERIVED_MANIFEST_V1\n");
        canonical.append("project=")
                .append(project.projectId())
                .append('|')
                .append(project.revision())
                .append('|')
                .append(project.schemaVersion())
                .append('|')
                .append(project.buildModelVersion())
                .append('\n');

        for (Map.Entry<String, String> entry
                : new TreeMap<>(project.resources()).entrySet()) {
            canonical.append("resource|")
                    .append(entry.getKey())
                    .append('|')
                    .append(sha256(entry.getValue()))
                    .append('\n');
        }
        for (Map.Entry<String, java.util.Set<String>> entry
                : new TreeMap<>(project.references()).entrySet()) {
            for (String target : new TreeSet<>(entry.getValue())) {
                canonical.append("reference|")
                        .append(entry.getKey())
                        .append('|')
                        .append(target)
                        .append('\n');
            }
        }
        for (String dependency
                : new TreeSet<>(project.dependencyRefs())) {
            canonical.append("dependency|")
                    .append(dependency)
                    .append('\n');
        }
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );
            byte[] bytes = digest.digest(
                    Objects.requireNonNull(value, "value")
                            .getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder out = new StringBuilder();
            for (byte item : bytes) {
                out.append(String.format(
                        java.util.Locale.ROOT,
                        "%02x",
                        item
                ));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
