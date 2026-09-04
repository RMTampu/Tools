package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class FlowPort {
    private final String portId;
    private final ValueType type;

    public FlowPort(String portId, ValueType type) {
        this.portId = StableId.require(portId, "portId");
        this.type = Objects.requireNonNull(type, "type");
    }

    public String portId() { return portId; }
    public ValueType type() { return type; }
}
