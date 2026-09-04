package com.toolbox.tools.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FlowValidator {
    public FlowValidationResult validate(FlowGraph graph) {
        List<String> issues = new ArrayList<>();
        for (FlowConnection connection : graph.connections()) {
            FlowNode from = graph.nodes().get(connection.fromNodeId());
            FlowNode to = graph.nodes().get(connection.toNodeId());
            if (from == null || to == null) {
                issues.add("BROKEN_FLOW_NODE_REFERENCE:" + connection.connectionId());
                continue;
            }
            FlowPort fromPort = from.outputs().get(connection.fromPortId());
            FlowPort toPort = to.inputs().get(connection.toPortId());
            if (fromPort == null || toPort == null) {
                issues.add("BROKEN_FLOW_PORT_REFERENCE:" + connection.connectionId());
                continue;
            }
            if (fromPort.type() != toPort.type()) {
                issues.add("FLOW_PORT_TYPE_MISMATCH:" + connection.connectionId());
            }
        }

        for (FlowNode node : graph.nodes().values()) {
            if (node.type() == FlowNodeType.BRANCH) {
                if (!node.outputs().containsKey("branch.true")
                        || !node.outputs().containsKey("branch.false")) {
                    issues.add("BRANCH_OUTPUT_INCOMPLETE:" + node.nodeId());
                }
            }
            if (node.type() == FlowNodeType.ASYNC) {
                String[] expected = {
                        "async.start",
                        "async.success",
                        "async.failure",
                        "async.cancelled",
                        "async.timeout"
                };
                for (String port : expected) {
                    if (!node.outputs().containsKey(port)) {
                        issues.add("ASYNC_OUTPUT_INCOMPLETE:" + node.nodeId());
                        break;
                    }
                }
            }
        }
        return new FlowValidationResult(issues);
    }
}
