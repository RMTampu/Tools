package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ComponentRegistry {
    private final Map<String, NavigableMap<VersionNumber, ComponentDefinition>> definitions =
            new LinkedHashMap<>();

    public synchronized void register(ComponentDefinition definition) {
        String id = StableId.require(definition.componentId(), "componentId");
        NavigableMap<VersionNumber, ComponentDefinition> versions =
                definitions.computeIfAbsent(id, ignored -> new TreeMap<>());
        if (versions.containsKey(definition.version())) {
            throw new IllegalArgumentException("component version already registered");
        }
        versions.put(definition.version(), definition);
    }

    public synchronized void publishReady(
            ComponentDefinition draft,
            ComponentValidator validator,
            AssetRegistry assetRegistry
    ) {
        if (draft.lifecycle() == CatalogLifecycle.ARCHIVED) {
            throw new IllegalArgumentException("archived component cannot become READY");
        }
        ComponentValidationResult validation = validator.validate(
                draft,
                assetRegistry,
                this
        );
        if (!validation.isPass()) {
            throw new IllegalArgumentException(
                    "component validation failed: " + validation.message()
            );
        }
        register(draft.withLifecycle(CatalogLifecycle.READY));
    }

    public synchronized ComponentDefinition resolveExact(
            String componentId,
            VersionNumber version
    ) {
        NavigableMap<VersionNumber, ComponentDefinition> versions =
                definitions.get(StableId.require(componentId, "componentId"));
        if (versions == null) return null;
        return versions.get(version);
    }

    public synchronized ComponentDefinition latestReady(String componentId) {
        NavigableMap<VersionNumber, ComponentDefinition> versions =
                definitions.get(StableId.require(componentId, "componentId"));
        if (versions == null) return null;
        for (ComponentDefinition definition : versions.descendingMap().values()) {
            if (definition.lifecycle() == CatalogLifecycle.READY) {
                return definition;
            }
        }
        return null;
    }

    public synchronized boolean hasCompatible(
            String componentId,
            VersionRange range
    ) {
        NavigableMap<VersionNumber, ComponentDefinition> versions =
                definitions.get(StableId.require(componentId, "componentId"));
        if (versions == null) return false;
        for (Map.Entry<VersionNumber, ComponentDefinition> entry : versions.entrySet()) {
            if (range.contains(entry.getKey())
                    && entry.getValue().lifecycle() == CatalogLifecycle.READY) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<ComponentDefinition> allVersions(String componentId) {
        NavigableMap<VersionNumber, ComponentDefinition> versions =
                definitions.get(StableId.require(componentId, "componentId"));
        if (versions == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(versions.values()));
    }

    public synchronized List<ComponentDefinition> allReady() {
        List<ComponentDefinition> out = new ArrayList<>();
        for (NavigableMap<VersionNumber, ComponentDefinition> versions : definitions.values()) {
            for (ComponentDefinition definition : versions.values()) {
                if (definition.lifecycle() == CatalogLifecycle.READY) out.add(definition);
            }
        }
        out.sort(Comparator.comparing(ComponentDefinition::componentId)
                .thenComparing(ComponentDefinition::version));
        return Collections.unmodifiableList(out);
    }
}
