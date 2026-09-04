package com.toolbox.tools.engine;

import com.toolbox.tools.core.EngineContract;
import com.toolbox.tools.runtime.FlowGraph;
import com.toolbox.tools.runtime.FlowValidationResult;
import com.toolbox.tools.runtime.FlowValidator;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import java.util.Objects;

public final class LogicToolEngine implements EngineContract {
    private final RuntimeEnvironment runtime;
    private final FlowValidator validator = new FlowValidator();

    public LogicToolEngine(RuntimeEnvironment runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String id() { return "engine.logic"; }
    @Override public boolean isReady() {
        for (FlowGraph flow : runtime.model().flows().values()) {
            FlowValidationResult result = validator.validate(flow);
            if (!result.isPass()) return false;
        }
        return true;
    }

    public int jumlahAlur() { return runtime.model().flows().size(); }
}
