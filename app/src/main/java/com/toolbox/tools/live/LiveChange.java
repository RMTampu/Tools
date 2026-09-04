package com.toolbox.tools.live;

import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.StableId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class LiveChange {
    private final String changeId;
    private final String resourceId;
    private final LiveChangeOperation operation;
    private final String payload;

    public LiveChange(
            String changeId,
            String resourceId,
            LiveChangeOperation operation,
            String payload
    ) {
        this.changeId = StableId.require(changeId, "changeId");
        this.resourceId = StableId.require(resourceId, "resourceId");
        this.operation = Objects.requireNonNull(operation, "operation");

        if (operation == LiveChangeOperation.UPSERT) {
            String value = Objects.requireNonNull(payload, "payload");
            if (value.getBytes(StandardCharsets.UTF_8).length
                    > ProjectState.MAX_RESOURCE_BYTES) {
                throw new IllegalArgumentException(
                        "live change payload too large"
                );
            }
            this.payload = value;
        } else {
            this.payload = null;
        }
    }

    public String changeId() { return changeId; }
    public String resourceId() { return resourceId; }
    public LiveChangeOperation operation() { return operation; }
    public String payload() { return payload; }
}
