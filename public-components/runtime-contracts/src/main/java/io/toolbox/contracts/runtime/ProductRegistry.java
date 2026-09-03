package io.toolbox.contracts.runtime;

import static io.toolbox.contracts.runtime.Contracts.ContractException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe metadata registry. Publication performs complete validation before
 * any committed map is changed, so a rejected ToolBundle cannot leave partial state.
 */
public final class ProductRegistry {
    public static final int MAX_REGISTRY_ENTRIES = 4096;

    private final Object lock = new Object();
    private final LinkedHashMap<String, Contracts.ToolContract> tools = new LinkedHashMap<>();
    private final LinkedHashMap<String, Contracts.ComponentContract> components = new LinkedHashMap<>();
    private final LinkedHashMap<String, Contracts.ActionContract> actions = new LinkedHashMap<>();
    private final LinkedHashMap<String, Contracts.CapabilityContract> capabilities = new LinkedHashMap<>();
    private final LinkedHashMap<String, Contracts.EventContract> events = new LinkedHashMap<>();
    private final LinkedHashMap<String, Contracts.PermissionRequirement> permissions = new LinkedHashMap<>();

    public RegistrySnapshot publish(Contracts.ToolBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        synchronized (lock) {
            validate(bundle);
            Contracts.ToolContract tool = bundle.tool();
            tools.put(tool.id(), tool);
            bundle.components().forEach(v -> components.put(v.id(), v));
            bundle.actions().forEach(v -> actions.put(v.id(), v));
            bundle.capabilities().forEach(v -> capabilities.put(v.id(), v));
            bundle.events().forEach(v -> events.put(v.id(), v));
            bundle.permissions().forEach(v -> permissions.put(v.id(), v));
            return snapshotLocked();
        }
    }

    public Optional<Contracts.ToolContract> tool(String id) {
        synchronized (lock) {
            return Optional.ofNullable(tools.get(Contracts.requireStableId(id, "toolId")));
        }
    }

    public Optional<Contracts.ComponentContract> component(String id) {
        synchronized (lock) {
            return Optional.ofNullable(components.get(Contracts.requireStableId(id, "componentId")));
        }
    }

    public Optional<Contracts.ActionContract> action(String id) {
        synchronized (lock) {
            return Optional.ofNullable(actions.get(Contracts.requireStableId(id, "actionId")));
        }
    }

    public Optional<Contracts.CapabilityContract> capability(String id) {
        synchronized (lock) {
            return Optional.ofNullable(capabilities.get(Contracts.requireStableId(id, "capabilityId")));
        }
    }

    public Optional<Contracts.EventContract> event(String id) {
        synchronized (lock) {
            return Optional.ofNullable(events.get(Contracts.requireStableId(id, "eventId")));
        }
    }

    public Optional<Contracts.PermissionRequirement> permission(String id) {
        synchronized (lock) {
            return Optional.ofNullable(permissions.get(Contracts.requireStableId(id, "permissionId")));
        }
    }

    public RegistrySnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    private void validate(Contracts.ToolBundle bundle) {
        Contracts.ToolContract tool = bundle.tool();

        requireUniqueBundleGlobalIds(bundle);
        requireRegistryCapacity(bundle.allIds().size());
        requireNewGlobalId(tool.id());
        requireUniqueNewIds(bundle.components(), "component");
        requireUniqueNewIds(bundle.actions(), "action");
        requireUniqueNewIds(bundle.capabilities(), "capability");
        requireUniqueNewIds(bundle.events(), "event");
        requireUniqueNewIds(bundle.permissions(), "permission");

        Set<String> bundleComponentIds = ids(bundle.components());
        Set<String> bundleActionIds = ids(bundle.actions());
        Set<String> bundleCapabilityIds = ids(bundle.capabilities());
        Set<String> bundleEventIds = ids(bundle.events());
        Set<String> bundlePermissionIds = ids(bundle.permissions());

        requireExactDeclaration("componentIds", tool.componentIds(), bundleComponentIds);
        requireExactDeclaration("actionIds", tool.actionIds(), bundleActionIds);
        requireExactDeclaration("capabilityIds", tool.capabilityIds(), bundleCapabilityIds);
        requireExactDeclaration("eventIds", tool.eventIds(), bundleEventIds);
        requireExactDeclaration("permissionIds", tool.permissionIds(), bundlePermissionIds);

        for (String dependency : tool.dependencies()) {
            if (!tools.containsKey(dependency)) {
                throw new ContractException("DEPENDENCY_MISSING", "Missing tool dependency: " + dependency);
            }
        }

        Set<String> availablePermissions = new LinkedHashSet<>(permissions.keySet());
        availablePermissions.addAll(bundlePermissionIds);
        Set<String> availableCapabilities = new LinkedHashSet<>(capabilities.keySet());
        availableCapabilities.addAll(bundleCapabilityIds);
        Set<String> availableEvents = new LinkedHashSet<>(events.keySet());
        availableEvents.addAll(bundleEventIds);

        bundle.components().forEach(component -> {
            requireProvider(tool.id(), component.providerToolId(), component.id());
            requireReferences(component.permissionNeeds(), availablePermissions, "PERMISSION_REFERENCE_MISSING", component.id());
            requireReferences(component.capabilityRequirements(), availableCapabilities, "CAPABILITY_UNAVAILABLE", component.id());
            requireReferences(component.eventIds(), availableEvents, "CONTRACT_INVALID", component.id());
        });

        bundle.actions().forEach(action -> {
            requireProvider(tool.id(), action.providerToolId(), action.id());
            requireReferences(action.permissionNeeds(), availablePermissions, "PERMISSION_REFERENCE_MISSING", action.id());
            requireReferences(action.capabilityRequirements(), availableCapabilities, "CAPABILITY_UNAVAILABLE", action.id());
        });

        bundle.capabilities().forEach(capability -> {
            requireProvider(tool.id(), capability.providerToolId(), capability.id());
            requireReferences(capability.permissionNeeds(), availablePermissions, "PERMISSION_REFERENCE_MISSING", capability.id());
        });

        bundle.events().forEach(event -> requireProvider(tool.id(), event.providerToolId(), event.id()));
    }

    private void requireRegistryCapacity(int incomingEntries) {
        int current = currentEntryCountLocked();
        if (incomingEntries > MAX_REGISTRY_ENTRIES - current) {
            throw new ContractException(
                    "RESOURCE_LIMIT",
                    "Registry capacity exceeded: current=" + current
                            + " incoming=" + incomingEntries
                            + " max=" + MAX_REGISTRY_ENTRIES
            );
        }
    }

    private int currentEntryCountLocked() {
        return tools.size() + components.size() + actions.size()
                + capabilities.size() + events.size() + permissions.size();
    }

    private static void requireUniqueBundleGlobalIds(Contracts.ToolBundle bundle) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        requireUniqueBundleId(seen, bundle.tool().id());
        bundle.components().forEach(value -> requireUniqueBundleId(seen, value.id()));
        bundle.actions().forEach(value -> requireUniqueBundleId(seen, value.id()));
        bundle.capabilities().forEach(value -> requireUniqueBundleId(seen, value.id()));
        bundle.events().forEach(value -> requireUniqueBundleId(seen, value.id()));
        bundle.permissions().forEach(value -> requireUniqueBundleId(seen, value.id()));
    }

    private static void requireUniqueBundleId(Set<String> seen, String id) {
        if (!seen.add(id)) {
            throw new ContractException("DUPLICATE_ID", "Duplicate Stable ID across bundle domains: " + id);
        }
    }

    private void requireUniqueNewIds(Collection<? extends Contracts.Identified> values, String domain) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Contracts.Identified value : values) {
            if (!seen.add(value.id())) {
                throw new ContractException("DUPLICATE_ID", "Duplicate " + domain + " ID in bundle: " + value.id());
            }
            requireNewGlobalId(value.id());
        }
    }

    private void requireNewGlobalId(String id) {
        if (tools.containsKey(id) || components.containsKey(id) || actions.containsKey(id)
                || capabilities.containsKey(id) || events.containsKey(id) || permissions.containsKey(id)) {
            throw new ContractException("DUPLICATE_ID", "Stable ID already committed: " + id);
        }
    }

    private static Set<String> ids(Collection<? extends Contracts.Identified> values) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        values.forEach(v -> ids.add(v.id()));
        return Collections.unmodifiableSet(ids);
    }

    private static void requireExactDeclaration(String field, Collection<String> declared, Set<String> actual) {
        LinkedHashSet<String> declaredSet = new LinkedHashSet<>(declared);
        if (!declaredSet.equals(actual)) {
            throw new ContractException(
                    "DECLARATION_MISMATCH",
                    field + " mismatch declared=" + declaredSet + " actual=" + actual
            );
        }
    }

    private static void requireProvider(String expected, String actual, String resourceId) {
        if (!expected.equals(actual)) {
            throw new ContractException(
                    "PROVIDER_MISMATCH",
                    "Provider mismatch for " + resourceId + ": expected=" + expected + " actual=" + actual
            );
        }
    }

    private static void requireReferences(
            Collection<String> required,
            Set<String> available,
            String failureCode,
            String ownerId
    ) {
        for (String id : required) {
            if (!available.contains(id)) {
                throw new ContractException(failureCode, ownerId + " references unavailable ID: " + id);
            }
        }
    }

    private RegistrySnapshot snapshotLocked() {
        return new RegistrySnapshot(
                copy(tools),
                copy(components),
                copy(actions),
                copy(capabilities),
                copy(events),
                copy(permissions)
        );
    }

    private static <T> Map<String, T> copy(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public static final class RegistrySnapshot {
        private final Map<String, Contracts.ToolContract> tools;
        private final Map<String, Contracts.ComponentContract> components;
        private final Map<String, Contracts.ActionContract> actions;
        private final Map<String, Contracts.CapabilityContract> capabilities;
        private final Map<String, Contracts.EventContract> events;
        private final Map<String, Contracts.PermissionRequirement> permissions;

        private RegistrySnapshot(
                Map<String, Contracts.ToolContract> tools,
                Map<String, Contracts.ComponentContract> components,
                Map<String, Contracts.ActionContract> actions,
                Map<String, Contracts.CapabilityContract> capabilities,
                Map<String, Contracts.EventContract> events,
                Map<String, Contracts.PermissionRequirement> permissions
        ) {
            this.tools = tools;
            this.components = components;
            this.actions = actions;
            this.capabilities = capabilities;
            this.events = events;
            this.permissions = permissions;
        }

        public Map<String, Contracts.ToolContract> tools() { return tools; }
        public Map<String, Contracts.ComponentContract> components() { return components; }
        public Map<String, Contracts.ActionContract> actions() { return actions; }
        public Map<String, Contracts.CapabilityContract> capabilities() { return capabilities; }
        public Map<String, Contracts.EventContract> events() { return events; }
        public Map<String, Contracts.PermissionRequirement> permissions() { return permissions; }

        public int totalEntries() {
            return tools.size() + components.size() + actions.size()
                    + capabilities.size() + events.size() + permissions.size();
        }
    }
}
