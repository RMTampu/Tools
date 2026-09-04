package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ActiveFlowMaterializer {
    private final Map<String, FlowGraph> metadata = new LinkedHashMap<>();

    public synchronized void register(FlowGraph graph) {
        Objects.requireNonNull(graph, "graph");
        if (metadata.put(graph.flowId(), graph) != null) {
            throw new IllegalArgumentException("flow already registered");
        }
    }

    public synchronized FlowGraph materialize(String flowId) {
        return metadata.get(StableId.require(flowId, "flowId"));
    }

    public synchronized Map<String, String> lightweightIndex() {
        LinkedHashMap<String, String> index = new LinkedHashMap<>();
        for (FlowGraph graph : metadata.values()) {
            index.put(graph.flowId(), graph.entryNodeId());
        }
        return Collections.unmodifiableMap(index);
    }
}
