package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;

public final class FlowConnection {
    private final String connectionId;
    private final String fromNodeId;
    private final String fromPortId;
    private final String toNodeId;
    private final String toPortId;

    public FlowConnection(
            String connectionId,
            String fromNodeId,
            String fromPortId,
            String toNodeId,
            String toPortId
    ) {
        this.connectionId = StableId.require(connectionId, "connectionId");
        this.fromNodeId = StableId.require(fromNodeId, "fromNodeId");
        this.fromPortId = StableId.require(fromPortId, "fromPortId");
        this.toNodeId = StableId.require(toNodeId, "toNodeId");
        this.toPortId = StableId.require(toPortId, "toPortId");
    }

    public String connectionId() { return connectionId; }
    public String fromNodeId() { return fromNodeId; }
    public String fromPortId() { return fromPortId; }
    public String toNodeId() { return toNodeId; }
    public String toPortId() { return toPortId; }
}
