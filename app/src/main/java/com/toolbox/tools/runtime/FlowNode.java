package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FlowNode {
    private final String nodeId;
    private final FlowNodeType type;
    private final Map<String, FlowPort> inputs;
    private final Map<String, FlowPort> outputs;
    private final int editorX;
    private final int editorY;
    private final int maxIterations;
    private final long timeoutMillis;

    public FlowNode(
            String nodeId,
            FlowNodeType type,
            Map<String, FlowPort> inputs,
            Map<String, FlowPort> outputs,
            int editorX,
            int editorY,
            int maxIterations,
            long timeoutMillis
    ) {
        this.nodeId = StableId.require(nodeId, "nodeId");
        this.type = Objects.requireNonNull(type, "type");
        this.inputs = immutablePorts(inputs);
        this.outputs = immutablePorts(outputs);
        this.editorX = editorX;
        this.editorY = editorY;

        if (type == FlowNodeType.LOOP) {
            if (maxIterations <= 0 || maxIterations > 10_000) {
                throw new IllegalArgumentException("loop iteration limit invalid");
            }
        } else if (maxIterations != 0) {
            throw new IllegalArgumentException("non-loop node cannot have iterations");
        }

        if ((type == FlowNodeType.ASYNC || type == FlowNodeType.LOOP)
                && (timeoutMillis <= 0 || timeoutMillis > 300_000)) {
            throw new IllegalArgumentException("node timeout invalid");
        }
        if (type != FlowNodeType.ASYNC
                && type != FlowNodeType.LOOP
                && timeoutMillis != 0) {
            throw new IllegalArgumentException("timeout only for async/loop");
        }
        this.maxIterations = maxIterations;
        this.timeoutMillis = timeoutMillis;
    }

    private static Map<String, FlowPort> immutablePorts(
            Map<String, FlowPort> input
    ) {
        LinkedHashMap<String, FlowPort> copy = new LinkedHashMap<>();
        if (input != null) {
            for (Map.Entry<String, FlowPort> entry : input.entrySet()) {
                String id = StableId.require(entry.getKey(), "portId");
                FlowPort port = Objects.requireNonNull(entry.getValue(), "port");
                if (!id.equals(port.portId())) {
                    throw new IllegalArgumentException("flow port identity mismatch");
                }
                if (copy.put(id, port) != null) {
                    throw new IllegalArgumentException("duplicate flow port");
                }
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public String nodeId() { return nodeId; }
    public FlowNodeType type() { return type; }
    public Map<String, FlowPort> inputs() { return inputs; }
    public Map<String, FlowPort> outputs() { return outputs; }
    public int editorX() { return editorX; }
    public int editorY() { return editorY; }
    public int maxIterations() { return maxIterations; }
    public long timeoutMillis() { return timeoutMillis; }
}
