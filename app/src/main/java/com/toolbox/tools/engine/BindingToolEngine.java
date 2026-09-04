package com.toolbox.tools.engine;

import com.toolbox.tools.core.EngineContract;
import com.toolbox.tools.runtime.BindingDefinition;
import com.toolbox.tools.runtime.BindingValidator;
import com.toolbox.tools.runtime.DataSourceDefinition;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import com.toolbox.tools.library.ComponentInstance;
import java.util.Objects;

public final class BindingToolEngine implements EngineContract {
    private final RuntimeEnvironment runtime;
    private final BindingValidator validator = new BindingValidator();

    public BindingToolEngine(RuntimeEnvironment runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String id() { return "engine.binding"; }

    @Override public boolean isReady() {
        for (BindingDefinition binding : runtime.model().bindings().values()) {
            DataSourceDefinition source =
                    runtime.model().dataSources().get(binding.sourceId());
            ComponentInstance target = findInstance(binding.targetInstanceId());
            if (!validator.isCompatible(
                    binding,
                    source,
                    target,
                    runtime.components()
            )) {
                return false;
            }
        }
        return true;
    }

    public int jumlahBinding() { return runtime.model().bindings().size(); }

    private ComponentInstance findInstance(String instanceId) {
        for (com.toolbox.tools.runtime.ScreenDefinition screen
                : runtime.model().screens().values()) {
            for (ComponentInstance item : screen.components()) {
                if (item.instanceId().equals(instanceId)) return item;
            }
        }
        return null;
    }
}
