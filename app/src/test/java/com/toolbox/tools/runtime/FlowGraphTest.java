package com.toolbox.tools.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class FlowGraphTest {
    @Test
    public void branchAsyncAndLoopContractsValidateExplicitly() {
        FlowPort input = new FlowPort("flow.in", ValueType.VOID);
        FlowPort branchTrue = new FlowPort("branch.true", ValueType.VOID);
        FlowPort branchFalse = new FlowPort("branch.false", ValueType.VOID);

        Map<String, FlowPort> branchOutputs = new LinkedHashMap<>();
        branchOutputs.put(branchTrue.portId(), branchTrue);
        branchOutputs.put(branchFalse.portId(), branchFalse);

        FlowNode branch = new FlowNode(
                "node.branch",
                FlowNodeType.BRANCH,
                Collections.singletonMap(input.portId(), input),
                branchOutputs,
                5,
                9,
                0,
                0
        );

        Map<String, FlowPort> asyncOutputs = new LinkedHashMap<>();
        for (String id : Arrays.asList(
                "async.start",
                "async.success",
                "async.failure",
                "async.cancelled",
                "async.timeout")) {
            asyncOutputs.put(id, new FlowPort(id, ValueType.VOID));
        }
        FlowNode async = new FlowNode(
                "node.async",
                FlowNodeType.ASYNC,
                Collections.singletonMap(input.portId(), input),
                asyncOutputs,
                50,
                20,
                0,
                5000
        );

        FlowNode loop = new FlowNode(
                "node.loop",
                FlowNodeType.LOOP,
                Collections.singletonMap(input.portId(), input),
                Collections.singletonMap("flow.out", new FlowPort(
                        "flow.out",
                        ValueType.VOID
                )),
                100,
                40,
                25,
                10000
        );

        FlowGraph graph = new FlowGraph(
                "flow.complex",
                "node.branch",
                Arrays.asList(branch, async, loop),
                Collections.emptyList()
        );

        assertTrue(new FlowValidator().validate(graph).isPass());
        assertEquals(25, loop.maxIterations());
        assertEquals(5000, async.timeoutMillis());
        assertEquals(5, branch.editorX());
    }

    @Test
    public void incompatiblePortConnectionFailsValidation() {
        FlowNode from = new FlowNode(
                "node.from",
                FlowNodeType.ACTION,
                Collections.emptyMap(),
                Collections.singletonMap(
                        "port.out",
                        new FlowPort("port.out", ValueType.TEXT)
                ),
                0,0,0,0
        );
        FlowNode to = new FlowNode(
                "node.to",
                FlowNodeType.ACTION,
                Collections.singletonMap(
                        "port.in",
                        new FlowPort("port.in", ValueType.NUMBER)
                ),
                Collections.emptyMap(),
                0,0,0,0
        );
        FlowGraph graph = new FlowGraph(
                "flow.bad",
                from.nodeId(),
                Arrays.asList(from, to),
                Collections.singletonList(
                        new FlowConnection(
                                "connection.bad",
                                from.nodeId(),
                                "port.out",
                                to.nodeId(),
                                "port.in"
                        )
                )
        );

        FlowValidationResult result = new FlowValidator().validate(graph);

        assertFalse(result.isPass());
        assertTrue(result.message().contains("FLOW_PORT_TYPE_MISMATCH"));
    }

    @Test
    public void watchdogStopsStepAndTimeRunaway() {
        FlowWatchdog steps = new FlowWatchdog(1000);
        for (int i = 0; i < FlowWatchdog.MAX_STEPS; i++) {
            steps.step(1000);
        }
        assertThrows(
                IllegalStateException.class,
                () -> steps.step(1000)
        );

        FlowWatchdog time = new FlowWatchdog(1000);
        assertThrows(
                IllegalStateException.class,
                () -> time.step(1000 + FlowWatchdog.MAX_RUNTIME_MILLIS + 1)
        );
    }

    @Test
    public void activeFlowMaterializerKeepsLightweightIndex() {
        FlowNode end = new FlowNode(
                "node.end",
                FlowNodeType.END,
                Collections.emptyMap(),
                Collections.emptyMap(),
                999,
                999,
                0,
                0
        );
        FlowGraph graph = new FlowGraph(
                "flow.one",
                end.nodeId(),
                Collections.singletonList(end),
                Collections.emptyList()
        );
        ActiveFlowMaterializer materializer = new ActiveFlowMaterializer();
        materializer.register(graph);

        assertEquals("node.end", materializer.lightweightIndex().get("flow.one"));
        assertEquals(graph, materializer.materialize("flow.one"));
    }
}
