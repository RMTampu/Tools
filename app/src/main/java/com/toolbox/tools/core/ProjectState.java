package com.toolbox.tools.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProjectState {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int CURRENT_BUILD_MODEL_VERSION = 1;
    public static final int MAX_RESOURCES = 4096;
    public static final int MAX_RESOURCE_BYTES = 262_144;
    public static final int MAX_REFERENCES = 16_384;
    public static final int MAX_DEPENDENCIES = 1024;

    private final String projectId;
    private final int schemaVersion;
    private final int buildModelVersion;
    private final long revision;
    private final ProjectLifecycle lifecycle;
    private final Map<String, String> resources;
    private final Map<String, Set<String>> references;
    private final Set<String> dependencyRefs;

    private ProjectState(
            String projectId,
            int schemaVersion,
            int buildModelVersion,
            long revision,
            ProjectLifecycle lifecycle,
            Map<String, String> resources,
            Map<String, Set<String>> references,
            Set<String> dependencyRefs
    ) {
        this.projectId = StableId.require(projectId, "projectId");
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be >= 0");
        }
        if (buildModelVersion < 0) {
            throw new IllegalArgumentException("buildModelVersion must be >= 0");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0");
        }
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.schemaVersion = schemaVersion;
        this.buildModelVersion = buildModelVersion;
        this.revision = revision;
        this.resources = immutableResources(resources);
        this.references = immutableReferences(references);
        this.dependencyRefs = immutableDependencies(dependencyRefs);
    }

    public static ProjectState create(String projectId) {
        return new ProjectState(
                projectId,
                CURRENT_SCHEMA_VERSION,
                CURRENT_BUILD_MODEL_VERSION,
                0,
                ProjectLifecycle.ACTIVE,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet()
        );
    }

    public static ProjectState restore(
            String projectId,
            int schemaVersion,
            int buildModelVersion,
            long revision,
            ProjectLifecycle lifecycle,
            Map<String, String> resources,
            Map<String, Set<String>> references,
            Set<String> dependencyRefs
    ) {
        return new ProjectState(
                projectId,
                schemaVersion,
                buildModelVersion,
                revision,
                lifecycle,
                resources,
                references,
                dependencyRefs
        );
    }

    ProjectState withSchemaVersion(int schemaVersion) {
        return new ProjectState(
                projectId,
                schemaVersion,
                buildModelVersion,
                revision,
                lifecycle,
                resources,
                references,
                dependencyRefs
        );
    }

    public ProjectState withRevision(long revision) {
        return new ProjectState(
                projectId,
                schemaVersion,
                buildModelVersion,
                revision,
                lifecycle,
                resources,
                references,
                dependencyRefs
        );
    }

    public ProjectState withLifecycle(ProjectLifecycle next) {
        return new ProjectState(
                projectId,
                schemaVersion,
                buildModelVersion,
                revision,
                next,
                resources,
                references,
                dependencyRefs
        );
    }

    public ProjectState withResource(String resourceId, String payload) {
        String id = StableId.require(resourceId, "resourceId");
        Objects.requireNonNull(payload, "payload");
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_RESOURCE_BYTES) {
            throw new IllegalArgumentException("resource exceeds size budget");
        }
        TreeMap<String, String> next = new TreeMap<>(resources);
        next.put(id, payload);
        return copy(next, references, dependencyRefs, lifecycle);
    }

    public ProjectState withoutResource(String resourceId) {
        String id = StableId.require(resourceId, "resourceId");
        TreeMap<String, String> nextResources = new TreeMap<>(resources);
        nextResources.remove(id);
        TreeMap<String, Set<String>> nextReferences = mutableReferences(references);
        nextReferences.remove(id);
        for (Map.Entry<String, Set<String>> entry : nextReferences.entrySet()) {
            entry.getValue().remove(id);
        }
        return copy(nextResources, nextReferences, dependencyRefs, lifecycle);
    }

    public ProjectState withReference(String sourceId, String targetId) {
        String source = StableId.require(sourceId, "sourceId");
        String target = StableId.require(targetId, "targetId");
        TreeMap<String, Set<String>> next = mutableReferences(references);
        next.computeIfAbsent(source, ignored -> new TreeSet<>()).add(target);
        return copy(resources, next, dependencyRefs, lifecycle);
    }

    public ProjectState withoutReference(String sourceId, String targetId) {
        String source = StableId.require(sourceId, "sourceId");
        String target = StableId.require(targetId, "targetId");
        TreeMap<String, Set<String>> next = mutableReferences(references);
        Set<String> targets = next.get(source);
        if (targets != null) {
            targets.remove(target);
            if (targets.isEmpty()) {
                next.remove(source);
            }
        }
        return copy(resources, next, dependencyRefs, lifecycle);
    }

    public ProjectState withDependency(String dependencyId) {
        TreeSet<String> next = new TreeSet<>(dependencyRefs);
        next.add(StableId.require(dependencyId, "dependencyId"));
        return copy(resources, references, next, lifecycle);
    }

    public ProjectState withoutDependency(String dependencyId) {
        TreeSet<String> next = new TreeSet<>(dependencyRefs);
        next.remove(StableId.require(dependencyId, "dependencyId"));
        return copy(resources, references, next, lifecycle);
    }

    private ProjectState copy(
            Map<String, String> nextResources,
            Map<String, ? extends Set<String>> nextReferences,
            Set<String> nextDependencies,
            ProjectLifecycle nextLifecycle
    ) {
        return new ProjectState(
                projectId,
                schemaVersion,
                buildModelVersion,
                revision,
                nextLifecycle,
                nextResources,
                normalizeReferences(nextReferences),
                nextDependencies
        );
    }

    public String projectId() {
        return projectId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public int buildModelVersion() {
        return buildModelVersion;
    }

    public long revision() {
        return revision;
    }

    public ProjectLifecycle lifecycle() {
        return lifecycle;
    }

    public Map<String, String> resources() {
        return resources;
    }

    public Map<String, Set<String>> references() {
        return references;
    }

    public Set<String> dependencyRefs() {
        return dependencyRefs;
    }

    private static Map<String, String> immutableResources(Map<String, String> input) {
        Objects.requireNonNull(input, "resources");
        if (input.size() > MAX_RESOURCES) {
            throw new IllegalArgumentException("too many resources");
        }
        TreeMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String id = StableId.require(entry.getKey(), "resourceId");
            String payload = Objects.requireNonNull(entry.getValue(), "resource payload");
            if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    > MAX_RESOURCE_BYTES) {
                throw new IllegalArgumentException("resource exceeds size budget");
            }
            copy.put(id, payload);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Set<String>> immutableReferences(
            Map<String, ? extends Set<String>> input
    ) {
        Objects.requireNonNull(input, "references");
        TreeMap<String, Set<String>> copy = new TreeMap<>();
        int count = 0;
        for (Map.Entry<String, ? extends Set<String>> entry : input.entrySet()) {
            String source = StableId.require(entry.getKey(), "reference source");
            TreeSet<String> targets = new TreeSet<>();
            for (String target : entry.getValue()) {
                targets.add(StableId.require(target, "reference target"));
                count++;
                if (count > MAX_REFERENCES) {
                    throw new IllegalArgumentException("too many references");
                }
            }
            if (!targets.isEmpty()) {
                copy.put(source, Collections.unmodifiableSet(targets));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Set<String>> normalizeReferences(
            Map<String, ? extends Set<String>> input
    ) {
        LinkedHashMap<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Set<String>> entry : input.entrySet()) {
            out.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return out;
    }

    private static TreeMap<String, Set<String>> mutableReferences(
            Map<String, Set<String>> input
    ) {
        TreeMap<String, Set<String>> out = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : input.entrySet()) {
            out.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return out;
    }

    private static Set<String> immutableDependencies(Set<String> input) {
        Objects.requireNonNull(input, "dependencyRefs");
        if (input.size() > MAX_DEPENDENCIES) {
            throw new IllegalArgumentException("too many dependencies");
        }
        TreeSet<String> copy = new TreeSet<>();
        for (String dependency : input) {
            copy.add(StableId.require(dependency, "dependencyId"));
        }
        return Collections.unmodifiableSet(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectState)) {
            return false;
        }
        ProjectState that = (ProjectState) other;
        return schemaVersion == that.schemaVersion
                && buildModelVersion == that.buildModelVersion
                && revision == that.revision
                && projectId.equals(that.projectId)
                && lifecycle == that.lifecycle
                && resources.equals(that.resources)
                && references.equals(that.references)
                && dependencyRefs.equals(that.dependencyRefs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                projectId,
                schemaVersion,
                buildModelVersion,
                revision,
                lifecycle,
                resources,
                references,
                dependencyRefs
        );
    }
}
