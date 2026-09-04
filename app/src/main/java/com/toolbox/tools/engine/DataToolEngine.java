package com.toolbox.tools.engine;

import com.toolbox.tools.core.EngineContract;
import com.toolbox.tools.runtime.DataSourceDefinition;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import java.util.Objects;

public final class DataToolEngine implements EngineContract {
    private final RuntimeEnvironment runtime;

    public DataToolEngine(RuntimeEnvironment runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String id() { return "engine.data"; }
    @Override public boolean isReady() {
        for (DataSourceDefinition source : runtime.model().dataSources().values()) {
            if (source.fields().isEmpty()) return false;
        }
        return true;
    }

    public int jumlahSumberData() { return runtime.model().dataSources().size(); }
}
