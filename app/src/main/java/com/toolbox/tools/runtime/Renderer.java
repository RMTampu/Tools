package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentDefinition;
import com.toolbox.tools.library.ComponentInstance;
import com.toolbox.tools.library.ComponentRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Renderer {
    public RenderTree materialize(
            ScreenDefinition screen,
            ComponentRegistry registry
    ) {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(registry, "registry");
        List<RenderNode> nodes = new ArrayList<>();
        List<RuntimeDiagnostic> diagnostics = new ArrayList<>();

        for (ComponentInstance instance : screen.components()) {
            ComponentDefinition definition = registry.resolveExact(
                    instance.componentId(),
                    instance.componentVersion()
            );
            boolean available = definition != null;
            Map<String, String> properties = new LinkedHashMap<>();
            if (available) {
                for (Map.Entry<String, com.toolbox.tools.library.PropertyContract> entry
                        : definition.properties().entrySet()) {
                    if (entry.getValue().defaultValue() != null) {
                        properties.put(
                                entry.getKey(),
                                entry.getValue().defaultValue()
                        );
                    }
                }
                properties.putAll(instance.propertyOverrides());
            } else {
                diagnostics.add(new RuntimeDiagnostic(
                        DiagnosticCode.COMPONENT_UNAVAILABLE,
                        instance.instanceId(),
                        "Component exact version unavailable"
                ));
            }
            nodes.add(new RenderNode(
                    instance.instanceId(),
                    instance.componentId(),
                    properties,
                    available
            ));
        }

        return new RenderTree(
                screen.screenId(),
                nodes,
                diagnostics
        );
    }
}
