package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FlowGraph {
    private final String flowId;
    private final String entryNodeId;
    private final Map<String, FlowNode> nodes;
    private final List<FlowConnection> connections;

    public FlowGraph(
            String flowId,
            String entryNodeId,
            List<FlowNode> nodes,
            List<FlowConnection> connections
    ) {
        this.flowId = StableId.require(flowId, "flowId");
        this.entryNodeId = StableId.require(entryNodeId, "entryNodeId");
        LinkedHashMap<String, FlowNode> nodeMap = new LinkedHashMap<>();
        for (FlowNode node : nodes == null
                ? Collections.<FlowNode>emptyList()
                : nodes) {
            Objects.requireNonNull(node, "node");
            if (nodeMap.put(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("duplicate flow node");
            }
        }
        if (!nodeMap.containsKey(this.entryNodeId)) {
            throw new IllegalArgumentException("entry node unavailable");
        }
        this.nodes = Collections.unmodifiableMap(nodeMap);
        this.connections = Collections.unmodifiableList(
                connections == null
                        ? new ArrayList<>()
                        : new ArrayList<>(connections)
        );
    }

    public String flowId() { return flowId; }
    public String entryNodeId() { return entryNodeId; }
    public Map<String, FlowNode> nodes() { return nodes; }
    public List<FlowConnection> connections() { return connections; }
}
