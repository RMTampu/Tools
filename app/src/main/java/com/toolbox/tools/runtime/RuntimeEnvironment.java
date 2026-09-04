package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentRegistry;
import java.util.Objects;

public final class RuntimeEnvironment {
    private final SharedRuntimeModel model;
    private final ActionRegistry actions;
    private final ComponentRegistry components;

    public RuntimeEnvironment(
            SharedRuntimeModel model,
            ActionRegistry actions,
            ComponentRegistry components
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.components = Objects.requireNonNull(components, "components");
    }

    public SharedRuntimeModel model() { return model; }
    public ActionRegistry actions() { return actions; }
    public ComponentRegistry components() { return components; }
}
