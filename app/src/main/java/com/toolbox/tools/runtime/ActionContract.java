package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ActionContract {
    private final String actionId;
    private final Map<String, ValueType> inputs;
    private final Map<String, ValueType> outputs;
    private final String permissionId;
    private final ExecutionMode executionMode;
    private final long timeoutMillis;
    private final boolean cancellable;
    private final boolean idempotent;

    public ActionContract(
            String actionId,
            Map<String, ValueType> inputs,
            Map<String, ValueType> outputs,
            String permissionId,
            ExecutionMode executionMode,
            long timeoutMillis,
            boolean cancellable,
            boolean idempotent
    ) {
        this.actionId = StableId.require(actionId, "actionId");
        this.inputs = immutableTypes(inputs, "inputId");
        this.outputs = immutableTypes(outputs, "outputId");
        this.permissionId = permissionId == null
                ? null
                : StableId.require(permissionId, "permissionId");
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode");
        if (timeoutMillis < 0 || timeoutMillis > 300_000) {
            throw new IllegalArgumentException("action timeout invalid");
        }
        this.timeoutMillis = timeoutMillis;
        this.cancellable = cancellable;
        this.idempotent = idempotent;
    }

    private static Map<String, ValueType> immutableTypes(
            Map<String, ValueType> input,
            String field
    ) {
        LinkedHashMap<String, ValueType> copy = new LinkedHashMap<>();
        if (input != null) {
            for (Map.Entry<String, ValueType> entry : input.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), field),
                        Objects.requireNonNull(entry.getValue(), "valueType")
                );
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public String actionId() { return actionId; }
    public Map<String, ValueType> inputs() { return inputs; }
    public Map<String, ValueType> outputs() { return outputs; }
    public String permissionId() { return permissionId; }
    public ExecutionMode executionMode() { return executionMode; }
    public long timeoutMillis() { return timeoutMillis; }
    public boolean cancellable() { return cancellable; }
    public boolean idempotent() { return idempotent; }
}
