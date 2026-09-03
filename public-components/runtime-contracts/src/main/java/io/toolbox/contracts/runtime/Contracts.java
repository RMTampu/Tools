package io.toolbox.contracts.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Metadata-only public contracts. No runtime callback, loader, filesystem, network,
 * signing, Firebase, or Android permission-grant authority exists in this API.
 */
public final class Contracts {
    private Contracts() {}

    public static final int MAX_STABLE_ID_LENGTH = 128;
    public static final int MAX_VERSION_LENGTH = 64;
    public static final int MAX_EXTERNAL_REF_LENGTH = 256;
    public static final int MAX_COLLECTION_SIZE = 256;
    public static final int MAX_BUNDLE_ENTRIES = 512;

    private static final Pattern STABLE_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?");

    public interface Identified {
        String id();
    }

    public enum PermissionKind {
        INSTALL_TIME,
        RUNTIME,
        SPECIAL_ACCESS,
        OPTIONAL
    }

    public static String requireStableId(String value, String field) {
        String id = requireBoundedText(value, field, MAX_STABLE_ID_LENGTH);
        if (!STABLE_ID.matcher(id).matches()) {
            throw new ContractException("CONTRACT_INVALID", field + " is not a valid Stable ID");
        }
        return id;
    }

    public static String requireVersion(String value, String field) {
        String version = requireBoundedText(value, field, MAX_VERSION_LENGTH);
        if (!VERSION.matcher(version).matches()) {
            throw new ContractException("CONTRACT_INVALID", field + " is not a semantic version");
        }
        return version;
    }

    public static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ContractException("CONTRACT_INVALID", field + " must not be blank");
        }
        return value.trim();
    }

    public static String requireBoundedText(String value, String field, int maxLength) {
        String text = requireText(value, field);
        if (text.length() > maxLength) {
            throw new ContractException(
                    "RESOURCE_LIMIT",
                    field + " exceeds maximum length " + maxLength + " (actual=" + text.length() + ")"
            );
        }
        return text;
    }

    public static String optionalBoundedText(String value, String field, int maxLength) {
        if (value == null) return "";
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new ContractException(
                    "RESOURCE_LIMIT",
                    field + " exceeds maximum length " + maxLength + " (actual=" + text.length() + ")"
            );
        }
        return text;
    }

    public static <T> T requireObject(T value, String field) {
        if (value == null) {
            throw new ContractException("CONTRACT_INVALID", field + " must not be null");
        }
        return value;
    }

    public static List<String> immutableStableIds(Collection<String> source, String field) {
        requireObject(source, field);
        ArrayList<String> copy = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : source) {
            if (copy.size() >= MAX_COLLECTION_SIZE) {
                throw new ContractException(
                        "RESOURCE_LIMIT",
                        field + " exceeds maximum entries " + MAX_COLLECTION_SIZE
                );
            }
            String id = requireStableId(raw, field);
            if (!seen.add(id)) {
                throw new ContractException("DUPLICATE_ID", "Duplicate " + field + ": " + id);
            }
            copy.add(id);
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class ContractException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String code;

        public ContractException(String code, String message) {
            super(message);
            this.code = requireText(code, "code");
        }

        public String code() {
            return code;
        }
    }

    public static final class PermissionRequirement implements Identified {
        private final String permissionId;
        private final PermissionKind kind;
        private final String platformPermissionRef;
        private final String reasonKey;
        private final String deniedBehavior;
        private final String unsupportedBehavior;

        public PermissionRequirement(
                String permissionId,
                PermissionKind kind,
                String platformPermissionRef,
                String reasonKey,
                String deniedBehavior,
                String unsupportedBehavior
        ) {
            this.permissionId = requireStableId(permissionId, "permissionId");
            this.kind = requireObject(kind, "kind");
            this.platformPermissionRef = optionalBoundedText(
                    platformPermissionRef,
                    "platformPermissionRef",
                    MAX_EXTERNAL_REF_LENGTH
            );
            this.reasonKey = requireStableId(reasonKey, "reasonKey");
            this.deniedBehavior = requireStableId(deniedBehavior, "deniedBehavior");
            this.unsupportedBehavior = requireStableId(unsupportedBehavior, "unsupportedBehavior");
        }

        @Override public String id() { return permissionId; }
        public PermissionKind kind() { return kind; }
        public String platformPermissionRef() { return platformPermissionRef; }
        public String reasonKey() { return reasonKey; }
        public String deniedBehavior() { return deniedBehavior; }
        public String unsupportedBehavior() { return unsupportedBehavior; }
    }

    public static final class CapabilityContract implements Identified {
        private final String capabilityId;
        private final String capabilityVersion;
        private final String contractVersion;
        private final String providerToolId;
        private final String compatibilityRef;
        private final List<String> permissionNeeds;

        public CapabilityContract(
                String capabilityId,
                String capabilityVersion,
                String contractVersion,
                String providerToolId,
                String compatibilityRef,
                Collection<String> permissionNeeds
        ) {
            this.capabilityId = requireStableId(capabilityId, "capabilityId");
            this.capabilityVersion = requireVersion(capabilityVersion, "capabilityVersion");
            this.contractVersion = requireVersion(contractVersion, "contractVersion");
            this.providerToolId = requireStableId(providerToolId, "providerToolId");
            this.compatibilityRef = requireStableId(compatibilityRef, "compatibilityRef");
            this.permissionNeeds = immutableStableIds(permissionNeeds, "permissionNeeds");
        }

        @Override public String id() { return capabilityId; }
        public String capabilityVersion() { return capabilityVersion; }
        public String contractVersion() { return contractVersion; }
        public String providerToolId() { return providerToolId; }
        public String compatibilityRef() { return compatibilityRef; }
        public List<String> permissionNeeds() { return permissionNeeds; }
    }

    public static final class EventContract implements Identified {
        private final String eventId;
        private final String contractVersion;
        private final String providerToolId;
        private final String payloadSchemaRef;
        private final String propagationPolicy;
        private final List<String> compatibleActionTypes;

        public EventContract(
                String eventId,
                String contractVersion,
                String providerToolId,
                String payloadSchemaRef,
                String propagationPolicy,
                Collection<String> compatibleActionTypes
        ) {
            this.eventId = requireStableId(eventId, "eventId");
            this.contractVersion = requireVersion(contractVersion, "contractVersion");
            this.providerToolId = requireStableId(providerToolId, "providerToolId");
            this.payloadSchemaRef = requireStableId(payloadSchemaRef, "payloadSchemaRef");
            this.propagationPolicy = requireStableId(propagationPolicy, "propagationPolicy");
            this.compatibleActionTypes = immutableStableIds(compatibleActionTypes, "compatibleActionTypes");
        }

        @Override public String id() { return eventId; }
        public String contractVersion() { return contractVersion; }
        public String providerToolId() { return providerToolId; }
        public String payloadSchemaRef() { return payloadSchemaRef; }
        public String propagationPolicy() { return propagationPolicy; }
        public List<String> compatibleActionTypes() { return compatibleActionTypes; }
    }

    public static final class ActionContract implements Identified {
        private final String actionId;
        private final String actionVersion;
        private final String contractVersion;
        private final String providerToolId;
        private final String inputSchemaRef;
        private final String outputSchemaRef;
        private final List<String> capabilityRequirements;
        private final List<String> permissionNeeds;
        private final String executionMode;
        private final String asyncBehavior;
        private final String timeoutPolicy;
        private final String cancellationPolicy;
        private final String idempotencyPolicy;

        public ActionContract(
                String actionId,
                String actionVersion,
                String contractVersion,
                String providerToolId,
                String inputSchemaRef,
                String outputSchemaRef,
                Collection<String> capabilityRequirements,
                Collection<String> permissionNeeds,
                String executionMode,
                String asyncBehavior,
                String timeoutPolicy,
                String cancellationPolicy,
                String idempotencyPolicy
        ) {
            this.actionId = requireStableId(actionId, "actionId");
            this.actionVersion = requireVersion(actionVersion, "actionVersion");
            this.contractVersion = requireVersion(contractVersion, "contractVersion");
            this.providerToolId = requireStableId(providerToolId, "providerToolId");
            this.inputSchemaRef = requireStableId(inputSchemaRef, "inputSchemaRef");
            this.outputSchemaRef = requireStableId(outputSchemaRef, "outputSchemaRef");
            this.capabilityRequirements = immutableStableIds(capabilityRequirements, "capabilityRequirements");
            this.permissionNeeds = immutableStableIds(permissionNeeds, "permissionNeeds");
            this.executionMode = requireStableId(executionMode, "executionMode");
            this.asyncBehavior = requireStableId(asyncBehavior, "asyncBehavior");
            this.timeoutPolicy = requireStableId(timeoutPolicy, "timeoutPolicy");
            this.cancellationPolicy = requireStableId(cancellationPolicy, "cancellationPolicy");
            this.idempotencyPolicy = requireStableId(idempotencyPolicy, "idempotencyPolicy");
        }

        @Override public String id() { return actionId; }
        public String actionVersion() { return actionVersion; }
        public String contractVersion() { return contractVersion; }
        public String providerToolId() { return providerToolId; }
        public String inputSchemaRef() { return inputSchemaRef; }
        public String outputSchemaRef() { return outputSchemaRef; }
        public List<String> capabilityRequirements() { return capabilityRequirements; }
        public List<String> permissionNeeds() { return permissionNeeds; }
        public String executionMode() { return executionMode; }
        public String asyncBehavior() { return asyncBehavior; }
        public String timeoutPolicy() { return timeoutPolicy; }
        public String cancellationPolicy() { return cancellationPolicy; }
        public String idempotencyPolicy() { return idempotencyPolicy; }
    }

    public static final class ComponentContract implements Identified {
        private final String componentId;
        private final String componentVersion;
        private final String contractVersion;
        private final String providerToolId;
        private final List<String> propertyContractIds;
        private final List<String> eventIds;
        private final List<String> capabilityRequirements;
        private final List<String> permissionNeeds;
        private final String implementationRef;

        public ComponentContract(
                String componentId,
                String componentVersion,
                String contractVersion,
                String providerToolId,
                Collection<String> propertyContractIds,
                Collection<String> eventIds,
                Collection<String> capabilityRequirements,
                Collection<String> permissionNeeds,
                String implementationRef
        ) {
            this.componentId = requireStableId(componentId, "componentId");
            this.componentVersion = requireVersion(componentVersion, "componentVersion");
            this.contractVersion = requireVersion(contractVersion, "contractVersion");
            this.providerToolId = requireStableId(providerToolId, "providerToolId");
            this.propertyContractIds = immutableStableIds(propertyContractIds, "propertyContractIds");
            this.eventIds = immutableStableIds(eventIds, "eventIds");
            this.capabilityRequirements = immutableStableIds(capabilityRequirements, "capabilityRequirements");
            this.permissionNeeds = immutableStableIds(permissionNeeds, "permissionNeeds");
            this.implementationRef = requireStableId(implementationRef, "implementationRef");
        }

        @Override public String id() { return componentId; }
        public String componentVersion() { return componentVersion; }
        public String contractVersion() { return contractVersion; }
        public String providerToolId() { return providerToolId; }
        public List<String> propertyContractIds() { return propertyContractIds; }
        public List<String> eventIds() { return eventIds; }
        public List<String> capabilityRequirements() { return capabilityRequirements; }
        public List<String> permissionNeeds() { return permissionNeeds; }
        public String implementationRef() { return implementationRef; }
    }

    public static final class ToolContract implements Identified {
        private final String toolId;
        private final String toolVersion;
        private final String contractVersion;
        private final List<String> dependencies;
        private final List<String> componentIds;
        private final List<String> actionIds;
        private final List<String> capabilityIds;
        private final List<String> eventIds;
        private final List<String> permissionIds;
        private final String entryPointId;

        public ToolContract(
                String toolId,
                String toolVersion,
                String contractVersion,
                Collection<String> dependencies,
                Collection<String> componentIds,
                Collection<String> actionIds,
                Collection<String> capabilityIds,
                Collection<String> eventIds,
                Collection<String> permissionIds,
                String entryPointId
        ) {
            this.toolId = requireStableId(toolId, "toolId");
            this.toolVersion = requireVersion(toolVersion, "toolVersion");
            this.contractVersion = requireVersion(contractVersion, "contractVersion");
            this.dependencies = immutableStableIds(dependencies, "dependencies");
            if (this.dependencies.contains(toolId)) {
                throw new ContractException("CONTRACT_INVALID", "Tool cannot depend on itself: " + toolId);
            }
            this.componentIds = immutableStableIds(componentIds, "componentIds");
            this.actionIds = immutableStableIds(actionIds, "actionIds");
            this.capabilityIds = immutableStableIds(capabilityIds, "capabilityIds");
            this.eventIds = immutableStableIds(eventIds, "eventIds");
            this.permissionIds = immutableStableIds(permissionIds, "permissionIds");
            this.entryPointId = requireStableId(entryPointId, "entryPointId");
        }

        @Override public String id() { return toolId; }
        public String toolVersion() { return toolVersion; }
        public String contractVersion() { return contractVersion; }
        public List<String> dependencies() { return dependencies; }
        public List<String> componentIds() { return componentIds; }
        public List<String> actionIds() { return actionIds; }
        public List<String> capabilityIds() { return capabilityIds; }
        public List<String> eventIds() { return eventIds; }
        public List<String> permissionIds() { return permissionIds; }
        public String entryPointId() { return entryPointId; }
    }

    public static final class ToolBundle {
        private final ToolContract tool;
        private final List<ComponentContract> components;
        private final List<ActionContract> actions;
        private final List<CapabilityContract> capabilities;
        private final List<EventContract> events;
        private final List<PermissionRequirement> permissions;

        public ToolBundle(
                ToolContract tool,
                Collection<ComponentContract> components,
                Collection<ActionContract> actions,
                Collection<CapabilityContract> capabilities,
                Collection<EventContract> events,
                Collection<PermissionRequirement> permissions
        ) {
            this.tool = requireObject(tool, "tool");
            this.components = immutableObjects(components, "components");
            this.actions = immutableObjects(actions, "actions");
            this.capabilities = immutableObjects(capabilities, "capabilities");
            this.events = immutableObjects(events, "events");
            this.permissions = immutableObjects(permissions, "permissions");
            int total = 1 + this.components.size() + this.actions.size()
                    + this.capabilities.size() + this.events.size() + this.permissions.size();
            if (total > MAX_BUNDLE_ENTRIES) {
                throw new ContractException(
                        "RESOURCE_LIMIT",
                        "ToolBundle exceeds maximum entries " + MAX_BUNDLE_ENTRIES + " (actual=" + total + ")"
                );
            }
        }

        private static <T> List<T> immutableObjects(Collection<T> source, String field) {
            requireObject(source, field);
            ArrayList<T> copy = new ArrayList<>();
            for (T value : source) {
                if (copy.size() >= MAX_COLLECTION_SIZE) {
                    throw new ContractException(
                            "RESOURCE_LIMIT",
                            field + " exceeds maximum entries " + MAX_COLLECTION_SIZE
                    );
                }
                copy.add(requireObject(value, field + " contains null"));
            }
            return Collections.unmodifiableList(copy);
        }

        public ToolContract tool() { return tool; }
        public List<ComponentContract> components() { return components; }
        public List<ActionContract> actions() { return actions; }
        public List<CapabilityContract> capabilities() { return capabilities; }
        public List<EventContract> events() { return events; }
        public List<PermissionRequirement> permissions() { return permissions; }

        public Set<String> allIds() {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            ids.add(tool.id());
            components.forEach(v -> ids.add(v.id()));
            actions.forEach(v -> ids.add(v.id()));
            capabilities.forEach(v -> ids.add(v.id()));
            events.forEach(v -> ids.add(v.id()));
            permissions.forEach(v -> ids.add(v.id()));
            return Collections.unmodifiableSet(ids);
        }
    }
}
