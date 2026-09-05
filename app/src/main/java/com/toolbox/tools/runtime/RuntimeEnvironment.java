package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentRegistry;
import java.util.ArrayList;
import java.util.Objects;

public final class RuntimeEnvironment {
    private final SharedRuntimeModel model;
    private final ActionRegistry actions;
    private final ComponentRegistry components;
    private final NavigationManager navigation;
    private final ValueConverterRegistry converters;

    public RuntimeEnvironment(
            SharedRuntimeModel model,
            ActionRegistry actions,
            ComponentRegistry components
    ) {
        this(
                model,
                actions,
                components,
                ValueConverterRegistry.defaults()
        );
    }

    public RuntimeEnvironment(
            SharedRuntimeModel model,
            ActionRegistry actions,
            ComponentRegistry components,
            ValueConverterRegistry converters
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.components = Objects.requireNonNull(components, "components");
        this.converters = Objects.requireNonNull(
                converters,
                "converters"
        );
        this.navigation = new NavigationManager(
                this.model,
                new ArrayList<>(this.model.routes().values())
        );
    }

    public SharedRuntimeModel model() { return model; }
    public ActionRegistry actions() { return actions; }
    public ComponentRegistry components() { return components; }
    public NavigationManager navigation() { return navigation; }
    public ValueConverterRegistry converters() { return converters; }
}
