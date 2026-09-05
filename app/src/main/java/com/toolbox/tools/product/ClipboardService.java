package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClipboardService {
    public static final class Clip {
        private final String sourceId;
        private final Map<String, String> properties;
        private final Set<String> dependencies;

        Clip(
                String sourceId,
                Map<String, String> properties,
                Set<String> dependencies
        ) {
            this.sourceId = sourceId;
            this.properties = Collections.unmodifiableMap(
                    new LinkedHashMap<>(properties)
            );
            this.dependencies = Collections.unmodifiableSet(
                    new LinkedHashSet<>(dependencies)
            );
        }

        public String sourceId() { return sourceId; }
        public Map<String, String> properties() {
            return properties;
        }
        public Set<String> dependencies() {
            return dependencies;
        }
    }

    public static final class PasteResult {
        private final String newId;
        private final Map<String, String> properties;
        private final Set<String> dependencies;
        private final Map<String, String> remap;
        private final List<String> brokenReferences;

        PasteResult(
                String newId,
                Map<String, String> properties,
                Set<String> dependencies,
                Map<String, String> remap,
                List<String> brokenReferences
        ) {
            this.newId = newId;
            this.properties = Collections.unmodifiableMap(
                    new LinkedHashMap<>(properties)
            );
            this.dependencies = Collections.unmodifiableSet(
                    new LinkedHashSet<>(dependencies)
            );
            this.remap = Collections.unmodifiableMap(
                    new LinkedHashMap<>(remap)
            );
            this.brokenReferences = Collections.unmodifiableList(
                    new ArrayList<>(brokenReferences)
            );
        }

        public String newId() { return newId; }
        public Map<String, String> properties() {
            return properties;
        }
        public Set<String> dependencies() {
            return dependencies;
        }
        public Map<String, String> remap() { return remap; }
        public List<String> brokenReferences() {
            return brokenReferences;
        }
        public boolean hasBrokenReferences() {
            return !brokenReferences.isEmpty();
        }
    }

    private Clip current;
    private long sequence;

    public synchronized void copy(
            String sourceId,
            Map<String, String> properties
    ) {
        copy(
                sourceId,
                properties,
                Collections.emptySet()
        );
    }

    public synchronized void copy(
            String sourceId,
            Map<String, String> properties,
            Set<String> dependencies
    ) {
        String id = StableId.require(
                sourceId,
                "sourceId"
        );
        LinkedHashMap<String, String> safeProperties =
                new LinkedHashMap<>();
        if (properties != null) {
            safeProperties.putAll(properties);
        }

        LinkedHashSet<String> safeDependencies =
                new LinkedHashSet<>();
        if (dependencies != null) {
            for (String dependency : dependencies) {
                safeDependencies.add(
                        StableId.require(
                                dependency,
                                "clipboardDependency"
                        )
                );
            }
        }
        current = new Clip(
                id,
                safeProperties,
                safeDependencies
        );
    }

    public synchronized String pasteNewId(
            String prefix
    ) {
        if (current == null) {
            throw new IllegalStateException(
                    "clipboard kosong"
            );
        }
        String stablePrefix = StableId.require(
                prefix,
                "prefix"
        );
        sequence++;
        return stablePrefix + ".copy." + sequence;
    }

    public synchronized PasteResult paste(
            String prefix,
            Set<String> existingIds,
            Map<String, String> exactDependencyRemap
    ) {
        if (current == null) {
            throw new IllegalStateException(
                    "clipboard kosong"
            );
        }
        Set<String> occupied = existingIds == null
                ? Collections.emptySet()
                : existingIds;
        Map<String, String> dependencyRemap =
                exactDependencyRemap == null
                        ? Collections.emptyMap()
                        : exactDependencyRemap;

        String newId;
        do {
            newId = pasteNewId(prefix);
        } while (occupied.contains(newId));

        LinkedHashMap<String, String> remap =
                new LinkedHashMap<>();
        remap.put(current.sourceId(), newId);

        LinkedHashSet<String> mappedDependencies =
                new LinkedHashSet<>();
        List<String> broken = new ArrayList<>();

        for (String dependency : current.dependencies()) {
            String mapped = dependencyRemap.get(dependency);
            if (mapped != null) {
                mappedDependencies.add(
                        StableId.require(
                                mapped,
                                "clipboardMappedDependency"
                        )
                );
                remap.put(dependency, mapped);
            } else if (occupied.contains(dependency)) {
                mappedDependencies.add(dependency);
            } else {
                broken.add(dependency);
            }
        }

        LinkedHashMap<String, String> pastedProperties =
                new LinkedHashMap<>();
        for (Map.Entry<String, String> entry
                : current.properties().entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                for (Map.Entry<String, String> mapping
                        : remap.entrySet()) {
                    if (value.equals(mapping.getKey())) {
                        value = mapping.getValue();
                        break;
                    }
                }
            }
            pastedProperties.put(entry.getKey(), value);
        }

        return new PasteResult(
                newId,
                pastedProperties,
                mappedDependencies,
                remap,
                broken
        );
    }

    public synchronized Clip current() {
        return current;
    }

    public synchronized boolean completeContract() {
        if (current == null) return true;
        return current.sourceId() != null
                && current.properties() != null
                && current.dependencies() != null;
    }
}
