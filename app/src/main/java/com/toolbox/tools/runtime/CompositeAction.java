package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompositeAction {
    private final String compositeActionId;
    private final List<String> orderedActionIds;
    private final String successConditionId;
    private final String failureActionId;
    private final String fallbackActionId;
    private final String compensationActionId;

    public CompositeAction(
            String compositeActionId,
            List<String> orderedActionIds,
            String successConditionId,
            String failureActionId,
            String fallbackActionId,
            String compensationActionId
    ) {
        this.compositeActionId = StableId.require(
                compositeActionId,
                "compositeActionId"
        );
        if (orderedActionIds == null || orderedActionIds.isEmpty()) {
            throw new IllegalArgumentException("composite action requires steps");
        }
        ArrayList<String> steps = new ArrayList<>();
        for (String id : orderedActionIds) {
            steps.add(StableId.require(id, "actionId"));
        }
        this.orderedActionIds = Collections.unmodifiableList(steps);
        this.successConditionId = successConditionId == null
                ? null
                : StableId.require(successConditionId, "successConditionId");
        this.failureActionId = optional(failureActionId, "failureActionId");
        this.fallbackActionId = optional(fallbackActionId, "fallbackActionId");
        this.compensationActionId = optional(
                compensationActionId,
                "compensationActionId"
        );
    }

    private static String optional(String value, String field) {
        return value == null ? null : StableId.require(value, field);
    }

    public String compositeActionId() { return compositeActionId; }
    public List<String> orderedActionIds() { return orderedActionIds; }
    public String successConditionId() { return successConditionId; }
    public String failureActionId() { return failureActionId; }
    public String fallbackActionId() { return fallbackActionId; }
    public String compensationActionId() { return compensationActionId; }
}
