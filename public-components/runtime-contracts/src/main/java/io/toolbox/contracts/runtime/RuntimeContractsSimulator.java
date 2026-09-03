package io.toolbox.contracts.runtime;

import java.util.Collections;
import java.util.List;

/** Metadata-only simulator. It deliberately performs no engine execution. */
public final class RuntimeContractsSimulator {
    private RuntimeContractsSimulator() {}

    public static void main(String[] args) {
        ProductRegistry registry = new ProductRegistry();
        ProductRegistry.RegistrySnapshot snapshot = registry.publish(demoBundle("tool.demo.editor", Collections.emptyList()));

        if (snapshot.tools().size() != 1
                || snapshot.components().size() != 1
                || snapshot.actions().size() != 1
                || snapshot.capabilities().size() != 1
                || snapshot.events().size() != 1
                || snapshot.permissions().size() != 1) {
            throw new AssertionError("Unexpected registry snapshot: " + snapshot.totalEntries());
        }

        if (!registry.action("action.demo.save").isPresent()) {
            throw new AssertionError("Exact action lookup failed");
        }

        System.out.println("PUBLIC_RUNTIME_CONTRACT_SIMULATOR = PASS");
        System.out.println("TOOLS=1 COMPONENTS=1 ACTIONS=1 CAPABILITIES=1 EVENTS=1 PERMISSIONS=1");
        System.out.println("ENGINE_CALLBACKS_EXECUTED=0");
    }

    static Contracts.ToolBundle demoBundle(String toolId, List<String> dependencies) {
        String permissionId = "permission.demo.storage";
        String capabilityId = "capability.demo.visual";
        String eventId = "event.demo.saved";
        String actionId = "action.demo.save";
        String componentId = "component.demo.button";

        Contracts.PermissionRequirement permission = new Contracts.PermissionRequirement(
                permissionId,
                Contracts.PermissionKind.OPTIONAL,
                "android.permission.read.media.images",
                "reason.demo.storage",
                "behavior.demo.readonly",
                "behavior.demo.unavailable"
        );
        Contracts.CapabilityContract capability = new Contracts.CapabilityContract(
                capabilityId,
                "1.0.0",
                "1.0.0",
                toolId,
                "compatibility.android.api30",
                List.of(permissionId)
        );
        Contracts.EventContract event = new Contracts.EventContract(
                eventId,
                "1.0.0",
                toolId,
                "schema.event.saved",
                "propagation.target.only",
                List.of("action.type.command")
        );
        Contracts.ActionContract action = new Contracts.ActionContract(
                actionId,
                "1.0.0",
                "1.0.0",
                toolId,
                "schema.action.save.input",
                "schema.action.save.output",
                List.of(capabilityId),
                List.of(permissionId),
                "execution.sync",
                "async.none",
                "timeout.not.applicable",
                "cancellation.not.applicable",
                "idempotency.execution.id"
        );
        Contracts.ComponentContract component = new Contracts.ComponentContract(
                componentId,
                "1.0.0",
                "1.0.0",
                toolId,
                List.of("property.demo.text"),
                List.of(eventId),
                List.of(capabilityId),
                List.of(permissionId),
                "implementation.demo.button"
        );
        Contracts.ToolContract tool = new Contracts.ToolContract(
                toolId,
                "0.1.0",
                "1.0.0",
                dependencies,
                List.of(componentId),
                List.of(actionId),
                List.of(capabilityId),
                List.of(eventId),
                List.of(permissionId),
                "entry.demo.editor"
        );
        return new Contracts.ToolBundle(
                tool,
                List.of(component),
                List.of(action),
                List.of(capability),
                List.of(event),
                List.of(permission)
        );
    }
}
