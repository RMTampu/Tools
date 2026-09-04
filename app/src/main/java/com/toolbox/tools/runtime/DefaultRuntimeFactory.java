package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentDefinition;
import com.toolbox.tools.library.ComponentInstance;
import com.toolbox.tools.library.ComponentRegistry;
import com.toolbox.tools.library.VersionNumber;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class DefaultRuntimeFactory {
    private DefaultRuntimeFactory() {
    }

    public static RuntimeEnvironment create(ComponentRegistry components) {
        ComponentDefinition button = components.resolveExact(
                "component.button",
                VersionNumber.parse("1.0.0")
        );
        if (button == null) {
            throw new IllegalStateException("default button component missing");
        }

        ComponentInstance buttonInstance = new ComponentInstance(
                "instance.home.primary",
                button.componentId(),
                button.version(),
                Collections.singletonMap("property.text", "Buka Detail")
        );

        ScreenDefinition home = new ScreenDefinition(
                "screen.home",
                "Beranda",
                Collections.singletonList(buttonInstance)
        );
        ScreenDefinition detail = new ScreenDefinition(
                "screen.detail",
                "Detail",
                Collections.emptyList()
        );

        LinkedHashMap<String, ScreenDefinition> screens = new LinkedHashMap<>();
        screens.put(home.screenId(), home);
        screens.put(detail.screenId(), detail);

        NavigationRoute detailRoute = new NavigationRoute(
                "route.detail",
                "screen.detail",
                Collections.singletonMap("parameter.item", ValueType.REFERENCE)
        );

        DataSourceDefinition source = new DataSourceDefinition(
                "data.items",
                "field.id",
                Arrays.asList(
                        new DataFieldDefinition(
                                "field.id",
                                ValueType.REFERENCE,
                                true
                        ),
                        new DataFieldDefinition(
                                "field.title",
                                ValueType.TEXT,
                                true
                        )
                )
        );

        BindingDefinition binding = new BindingDefinition(
                "binding.home.title",
                "data.items",
                "field.title",
                "instance.home.primary",
                "property.text",
                ValueType.TEXT,
                BindingMode.ONE_WAY
        );

        EventDefinition click = new EventDefinition(
                "event.home.click",
                Collections.singletonMap(
                        "parameter.item",
                        ValueType.REFERENCE
                )
        );

        ActionRegistry actions = new ActionRegistry();
        actions.register(new ActionContract(
                "action.navigation.open",
                Collections.singletonMap(
                        "parameter.item",
                        ValueType.REFERENCE
                ),
                Collections.emptyMap(),
                null,
                ExecutionMode.SYNC,
                0,
                false,
                true
        ));

        FlowPort voidOut = new FlowPort("flow.out", ValueType.VOID);
        FlowPort voidIn = new FlowPort("flow.in", ValueType.VOID);
        FlowNode eventNode = new FlowNode(
                "node.event",
                FlowNodeType.EVENT,
                Collections.emptyMap(),
                Collections.singletonMap(voidOut.portId(), voidOut),
                0,
                0,
                0,
                0
        );
        FlowNode actionNode = new FlowNode(
                "node.action",
                FlowNodeType.ACTION,
                Collections.singletonMap(voidIn.portId(), voidIn),
                Collections.emptyMap(),
                100,
                0,
                0,
                0
        );
        FlowGraph flow = new FlowGraph(
                "flow.home.click",
                eventNode.nodeId(),
                Arrays.asList(eventNode, actionNode),
                Collections.singletonList(
                        new FlowConnection(
                                "connection.event.action",
                                eventNode.nodeId(),
                                voidOut.portId(),
                                actionNode.nodeId(),
                                voidIn.portId()
                        )
                )
        );

        SharedRuntimeModel model = new SharedRuntimeModel(
                screens,
                "screen.home",
                Collections.singletonList(detailRoute),
                Collections.singletonList(source),
                Collections.singletonList(binding),
                Collections.singletonList(flow),
                Collections.singletonList(click),
                Collections.singletonList(
                        new EventActionBinding(
                                "binding.event.home.click",
                                click.eventId(),
                                "action.navigation.open"
                        )
                )
        );

        return new RuntimeEnvironment(model, actions, components);
    }
}
