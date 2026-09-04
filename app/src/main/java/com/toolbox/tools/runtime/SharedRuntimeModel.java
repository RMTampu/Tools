package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SharedRuntimeModel {
    private final Map<String, ScreenDefinition> screens;
    private final String startScreenId;
    private final Map<String, NavigationRoute> routes;
    private final Map<String, DataSourceDefinition> dataSources;
    private final Map<String, BindingDefinition> bindings;
    private final Map<String, FlowGraph> flows;
    private final Map<String, EventDefinition> events;
    private final List<EventActionBinding> eventActionBindings;

    public SharedRuntimeModel(
            Map<String, ScreenDefinition> screens,
            String startScreenId
    ) {
        this(
                screens,
                startScreenId,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public SharedRuntimeModel(
            Map<String, ScreenDefinition> screens,
            String startScreenId,
            List<NavigationRoute> routes,
            List<DataSourceDefinition> dataSources,
            List<BindingDefinition> bindings,
            List<FlowGraph> flows,
            List<EventDefinition> events,
            List<EventActionBinding> eventActionBindings
    ) {
        this.screens = immutableById(
                Objects.requireNonNull(screens, "screens")
        );
        this.startScreenId = StableId.require(startScreenId, "startScreenId");
        if (!this.screens.containsKey(this.startScreenId)) {
            throw new IllegalArgumentException("start screen unavailable");
        }
        this.routes = immutableRoutes(routes);
        this.dataSources = immutableDataSources(dataSources);
        this.bindings = immutableBindings(bindings);
        this.flows = immutableFlows(flows);
        this.events = immutableEvents(events);
        this.eventActionBindings = Collections.unmodifiableList(
                eventActionBindings == null
                        ? new ArrayList<>()
                        : new ArrayList<>(eventActionBindings)
        );
    }

    private static Map<String, ScreenDefinition> immutableById(
            Map<String, ScreenDefinition> input
    ) {
        LinkedHashMap<String, ScreenDefinition> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ScreenDefinition> entry : input.entrySet()) {
            String id = StableId.require(entry.getKey(), "screenId");
            ScreenDefinition screen = Objects.requireNonNull(
                    entry.getValue(),
                    "screen"
            );
            if (!id.equals(screen.screenId())) {
                throw new IllegalArgumentException("screen map identity mismatch");
            }
            if (copy.put(id, screen) != null) {
                throw new IllegalArgumentException("duplicate screen");
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, NavigationRoute> immutableRoutes(
            List<NavigationRoute> input
    ) {
        LinkedHashMap<String, NavigationRoute> out = new LinkedHashMap<>();
        for (NavigationRoute route : input == null
                ? Collections.<NavigationRoute>emptyList()
                : input) {
            if (out.put(route.routeId(), route) != null) {
                throw new IllegalArgumentException("duplicate route");
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, DataSourceDefinition> immutableDataSources(
            List<DataSourceDefinition> input
    ) {
        LinkedHashMap<String, DataSourceDefinition> out = new LinkedHashMap<>();
        for (DataSourceDefinition source : input == null
                ? Collections.<DataSourceDefinition>emptyList()
                : input) {
            if (out.put(source.sourceId(), source) != null) {
                throw new IllegalArgumentException("duplicate data source");
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, BindingDefinition> immutableBindings(
            List<BindingDefinition> input
    ) {
        LinkedHashMap<String, BindingDefinition> out = new LinkedHashMap<>();
        for (BindingDefinition binding : input == null
                ? Collections.<BindingDefinition>emptyList()
                : input) {
            if (out.put(binding.bindingId(), binding) != null) {
                throw new IllegalArgumentException("duplicate binding");
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, FlowGraph> immutableFlows(
            List<FlowGraph> input
    ) {
        LinkedHashMap<String, FlowGraph> out = new LinkedHashMap<>();
        for (FlowGraph flow : input == null
                ? Collections.<FlowGraph>emptyList()
                : input) {
            if (out.put(flow.flowId(), flow) != null) {
                throw new IllegalArgumentException("duplicate flow");
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, EventDefinition> immutableEvents(
            List<EventDefinition> input
    ) {
        LinkedHashMap<String, EventDefinition> out = new LinkedHashMap<>();
        for (EventDefinition event : input == null
                ? Collections.<EventDefinition>emptyList()
                : input) {
            if (out.put(event.eventId(), event) != null) {
                throw new IllegalArgumentException("duplicate event");
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public Map<String, ScreenDefinition> screens() { return screens; }
    public String startScreenId() { return startScreenId; }
    public ScreenDefinition screen(String screenId) {
        return screens.get(StableId.require(screenId, "screenId"));
    }
    public Map<String, NavigationRoute> routes() { return routes; }
    public Map<String, DataSourceDefinition> dataSources() { return dataSources; }
    public Map<String, BindingDefinition> bindings() { return bindings; }
    public Map<String, FlowGraph> flows() { return flows; }
    public Map<String, EventDefinition> events() { return events; }
    public List<EventActionBinding> eventActionBindings() {
        return eventActionBindings;
    }
}
