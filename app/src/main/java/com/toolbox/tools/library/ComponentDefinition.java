package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ComponentDefinition {
    private final String componentId;
    private final String labelId;
    private final String labelIndonesia;
    private final String categoryId;
    private final String iconAssetId;
    private final VersionNumber version;
    private final CatalogLifecycle lifecycle;
    private final String implementationRef;
    private final Map<String, PropertyContract> properties;
    private final Map<String, EventContract> events;
    private final StateContract stateContract;
    private final BindingContract bindingContract;
    private final AccessibilityContract accessibilityContract;
    private final Set<String> capabilityRequirements;
    private final List<AssetDependencyRef> assetDependencies;
    private final List<DependencyRef> dependencies;

    public ComponentDefinition(
            String componentId,
            String labelId,
            String labelIndonesia,
            String categoryId,
            String iconAssetId,
            VersionNumber version,
            CatalogLifecycle lifecycle,
            String implementationRef,
            List<PropertyContract> properties,
            List<EventContract> events,
            StateContract stateContract,
            BindingContract bindingContract,
            AccessibilityContract accessibilityContract,
            Set<String> capabilityRequirements,
            List<AssetDependencyRef> assetDependencies,
            List<DependencyRef> dependencies
    ) {
        this.componentId = StableId.require(componentId, "componentId");
        this.labelId = StableId.require(labelId, "labelId");
        this.labelIndonesia = requireLabel(labelIndonesia);
        this.categoryId = StableId.require(categoryId, "categoryId");
        this.iconAssetId = iconAssetId == null
                ? null
                : StableId.require(iconAssetId, "iconAssetId");
        this.version = Objects.requireNonNull(version, "version");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.implementationRef = StableId.require(
                implementationRef,
                "implementationRef"
        );

        LinkedHashMap<String, PropertyContract> propertyMap = new LinkedHashMap<>();
        if (properties != null) {
            for (PropertyContract property : properties) {
                if (propertyMap.put(property.propertyId(), property) != null) {
                    throw new IllegalArgumentException("duplicate propertyId");
                }
            }
        }
        this.properties = Collections.unmodifiableMap(propertyMap);

        LinkedHashMap<String, EventContract> eventMap = new LinkedHashMap<>();
        if (events != null) {
            for (EventContract event : events) {
                if (eventMap.put(event.eventId(), event) != null) {
                    throw new IllegalArgumentException("duplicate eventId");
                }
            }
        }
        this.events = Collections.unmodifiableMap(eventMap);

        this.stateContract = Objects.requireNonNull(stateContract, "stateContract");
        this.bindingContract = Objects.requireNonNull(bindingContract, "bindingContract");
        this.accessibilityContract = Objects.requireNonNull(
                accessibilityContract,
                "accessibilityContract"
        );
        this.capabilityRequirements = immutableIds(
                capabilityRequirements,
                "capabilityRequirement"
        );
        this.assetDependencies = Collections.unmodifiableList(
                assetDependencies == null
                        ? new ArrayList<>()
                        : new ArrayList<>(assetDependencies)
        );
        this.dependencies = Collections.unmodifiableList(
                dependencies == null
                        ? new ArrayList<>()
                        : new ArrayList<>(dependencies)
        );
    }

    private static String requireLabel(String value) {
        Objects.requireNonNull(value, "labelIndonesia");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new IllegalArgumentException("component label invalid");
        }
        return trimmed;
    }

    private static Set<String> immutableIds(Set<String> input, String field) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (input != null) {
            for (String item : input) {
                out.add(StableId.require(item, field));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public String componentId() { return componentId; }
    public String labelId() { return labelId; }
    public String labelIndonesia() { return labelIndonesia; }
    public String categoryId() { return categoryId; }
    public String iconAssetId() { return iconAssetId; }
    public VersionNumber version() { return version; }
    public CatalogLifecycle lifecycle() { return lifecycle; }
    public String implementationRef() { return implementationRef; }
    public Map<String, PropertyContract> properties() { return properties; }
    public Map<String, EventContract> events() { return events; }
    public StateContract stateContract() { return stateContract; }
    public BindingContract bindingContract() { return bindingContract; }
    public AccessibilityContract accessibilityContract() { return accessibilityContract; }
    public Set<String> capabilityRequirements() { return capabilityRequirements; }
    public List<AssetDependencyRef> assetDependencies() { return assetDependencies; }
    public List<DependencyRef> dependencies() { return dependencies; }

    public ComponentDefinition withLifecycle(CatalogLifecycle next) {
        return new ComponentDefinition(
                componentId,
                labelId,
                labelIndonesia,
                categoryId,
                iconAssetId,
                version,
                next,
                implementationRef,
                new ArrayList<>(properties.values()),
                new ArrayList<>(events.values()),
                stateContract,
                bindingContract,
                accessibilityContract,
                capabilityRequirements,
                assetDependencies,
                dependencies
        );
    }
}
