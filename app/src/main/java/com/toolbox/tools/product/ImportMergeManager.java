package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectLifecycle;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ImportMergeManager {
    public enum Mode {
        IMPORT_NEW,
        MERGE_EXISTING
    }

    public static final class Result {
        private final String projectId;
        private final Map<String, String> idMap;
        private final ProjectState projectState;
        private final Mode mode;

        Result(
                String projectId,
                Map<String, String> idMap,
                ProjectState projectState,
                Mode mode
        ) {
            this.projectId = projectId;
            this.idMap = Collections.unmodifiableMap(
                    new LinkedHashMap<>(idMap)
            );
            this.projectState = projectState;
            this.mode = mode;
        }

        public String projectId() { return projectId; }
        public Map<String, String> idMap() { return idMap; }
        public ProjectState projectState() { return projectState; }
        public Mode mode() { return mode; }
    }

    private final ProjectValidator validator = new ProjectValidator();

    /**
     * Compatibility contract used by older callers that only need the ID map.
     */
    public synchronized Result importAsNew(
            String projectId,
            Iterable<String> stableIds
    ) {
        String project = StableId.require(projectId, "projectId");
        LinkedHashMap<String, String> map = identityMap(stableIds);
        return new Result(
                project,
                map,
                null,
                Mode.IMPORT_NEW
        );
    }

    /**
     * Import as a new project preserves the incoming project ID and every
     * internal Stable ID. Revision is reset to 0 because the destination
     * ProjectStore owns its own append-only revision sequence.
     */
    public synchronized Result importAsNew(ProjectState incoming) {
        requireImportable(incoming, "incoming");
        LinkedHashMap<String, String> map = identityMap(
                ownedIds(incoming)
        );
        ProjectState staged = ProjectState.restore(
                incoming.projectId(),
                incoming.schemaVersion(),
                incoming.buildModelVersion(),
                0,
                ProjectLifecycle.ACTIVE,
                incoming.resources(),
                incoming.references(),
                incoming.dependencyRefs()
        );
        requireValid(staged, "import");
        return new Result(
                staged.projectId(),
                map,
                staged,
                Mode.IMPORT_NEW
        );
    }

    /**
     * Compatibility ID-map API. Mapping is deterministic for identical sets.
     */
    public synchronized Result mergeInto(
            String targetProjectId,
            Iterable<String> incomingIds,
            Iterable<String> existingIds
    ) {
        String project = StableId.require(
                targetProjectId,
                "projectId"
        );
        LinkedHashSet<String> existing = stableSet(
                existingIds,
                "existingId"
        );
        LinkedHashMap<String, String> map =
                buildRemap(incomingIds, existing);
        return new Result(
                project,
                map,
                null,
                Mode.MERGE_EXISTING
        );
    }

    /**
     * Merge is performed on a staged ProjectState. Conflicting incoming
     * resource/reference identities are remapped, and every graph reference
     * pointing at an incoming-owned identity is rewritten through the same
     * deterministic ID map. The target is not mutated by this method.
     */
    public synchronized Result mergeInto(
            ProjectState target,
            ProjectState incoming
    ) {
        requireImportable(target, "target");
        requireImportable(incoming, "incoming");

        if (target.schemaVersion() != incoming.schemaVersion()) {
            throw new IllegalArgumentException(
                    "merge schema version mismatch"
            );
        }
        if (target.buildModelVersion()
                != incoming.buildModelVersion()) {
            throw new IllegalArgumentException(
                    "merge build model mismatch"
            );
        }
        if (target.lifecycle() != ProjectLifecycle.ACTIVE
                || incoming.lifecycle() == ProjectLifecycle.TRASH) {
            throw new IllegalArgumentException(
                    "merge lifecycle incompatible"
            );
        }

        LinkedHashSet<String> occupied = new LinkedHashSet<>();
        occupied.addAll(target.resources().keySet());
        occupied.addAll(target.references().keySet());
        for (Set<String> targets : target.references().values()) {
            occupied.addAll(targets);
        }

        LinkedHashSet<String> incomingOwned = ownedIds(incoming);
        LinkedHashMap<String, String> idMap =
                buildRemap(incomingOwned, occupied);

        TreeMap<String, String> resources =
                new TreeMap<>(target.resources());
        for (Map.Entry<String, String> entry
                : new TreeMap<>(incoming.resources()).entrySet()) {
            String mapped = mapId(idMap, entry.getKey());
            if (resources.put(mapped, entry.getValue()) != null) {
                throw new IllegalStateException(
                        "merge resource collision after remap"
                );
            }
        }

        TreeMap<String, Set<String>> references =
                mutableReferences(target.references());
        for (Map.Entry<String, Set<String>> entry
                : new TreeMap<>(incoming.references()).entrySet()) {
            String source = mapId(idMap, entry.getKey());
            Set<String> mappedTargets = references.computeIfAbsent(
                    source,
                    ignored -> new TreeSet<>()
            );
            for (String targetId : entry.getValue()) {
                mappedTargets.add(mapId(idMap, targetId));
            }
        }

        TreeSet<String> dependencies =
                new TreeSet<>(target.dependencyRefs());
        dependencies.addAll(incoming.dependencyRefs());

        ProjectState staged = ProjectState.restore(
                target.projectId(),
                target.schemaVersion(),
                target.buildModelVersion(),
                target.revision(),
                target.lifecycle(),
                resources,
                references,
                dependencies
        );
        requireValid(staged, "merge");
        return new Result(
                target.projectId(),
                idMap,
                staged,
                Mode.MERGE_EXISTING
        );
    }

    private void requireImportable(
            ProjectState state,
            String label
    ) {
        if (state == null) throw new NullPointerException(label);
        if (state.schemaVersion()
                != ProjectState.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    label + " schema incompatible"
            );
        }
        if (state.buildModelVersion()
                != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            throw new IllegalArgumentException(
                    label + " build model incompatible"
            );
        }
        requireValid(state, label);
    }

    private void requireValid(
            ProjectState state,
            String label
    ) {
        ProjectValidationResult result = validator.validate(state);
        if (!result.isPass()) {
            throw new IllegalArgumentException(
                    label + " project invalid:"
                            + result.message()
            );
        }
    }

    private static LinkedHashMap<String, String> identityMap(
            Iterable<String> ids
    ) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String stable : stableSet(ids, "stableId")) {
            map.put(stable, stable);
        }
        return map;
    }

    private static LinkedHashMap<String, String> buildRemap(
            Iterable<String> incomingIds,
            Set<String> existing
    ) {
        LinkedHashSet<String> incoming =
                stableSet(incomingIds, "incomingId");
        LinkedHashSet<String> reserved =
                new LinkedHashSet<>(existing);
        LinkedHashMap<String, String> map =
                new LinkedHashMap<>();

        for (String stable : new TreeSet<>(incoming)) {
            String mapped = stable;
            if (reserved.contains(mapped)) {
                int sequence = 1;
                do {
                    mapped = stable + ".import." + sequence++;
                    StableId.require(mapped, "remappedId");
                } while (reserved.contains(mapped));
            }
            reserved.add(mapped);
            map.put(stable, mapped);
        }
        return map;
    }

    private static LinkedHashSet<String> ownedIds(ProjectState state) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.addAll(state.resources().keySet());
        out.addAll(state.references().keySet());

        // A target ID is considered incoming-owned only if it is also a
        // resource/source defined by that incoming project. References to
        // registries/external dependencies remain stable and are not guessed.
        Set<String> definitions = new LinkedHashSet<>();
        definitions.addAll(state.resources().keySet());
        definitions.addAll(state.references().keySet());
        for (Set<String> targets : state.references().values()) {
            for (String target : targets) {
                if (definitions.contains(target)) out.add(target);
            }
        }
        return out;
    }

    private static LinkedHashSet<String> stableSet(
            Iterable<String> values,
            String label
    ) {
        if (values == null) throw new NullPointerException(label);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            out.add(StableId.require(value, label));
        }
        return out;
    }

    private static String mapId(
            Map<String, String> map,
            String value
    ) {
        String stable = StableId.require(value, "referenceId");
        String mapped = map.get(stable);
        return mapped == null ? stable : mapped;
    }

    private static TreeMap<String, Set<String>> mutableReferences(
            Map<String, Set<String>> source
    ) {
        TreeMap<String, Set<String>> out = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry
                : source.entrySet()) {
            out.put(
                    entry.getKey(),
                    new TreeSet<>(entry.getValue())
            );
        }
        return out;
    }
}
