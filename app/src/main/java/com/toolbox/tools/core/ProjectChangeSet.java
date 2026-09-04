package com.toolbox.tools.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ProjectChangeSet {
    private final Map<String, String> beforeValues;
    private final Set<String> beforeMissing;
    private final Map<String, String> afterValues;
    private final Set<String> afterMissing;

    ProjectChangeSet(
            Map<String, String> beforeValues,
            Set<String> beforeMissing,
            Map<String, String> afterValues,
            Set<String> afterMissing
    ) {
        this.beforeValues = Collections.unmodifiableMap(new LinkedHashMap<>(beforeValues));
        this.beforeMissing = Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(beforeMissing)
        );
        this.afterValues = Collections.unmodifiableMap(new LinkedHashMap<>(afterValues));
        this.afterMissing = Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(afterMissing)
        );
    }

    ProjectState applyForward(ProjectState state) {
        return apply(state, afterValues, afterMissing);
    }

    ProjectState applyReverse(ProjectState state) {
        return apply(state, beforeValues, beforeMissing);
    }

    private static ProjectState apply(
            ProjectState state,
            Map<String, String> values,
            Set<String> missing
    ) {
        ProjectState next = state;
        for (String id : missing) {
            next = next.withoutResource(id);
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            next = next.withResource(entry.getKey(), entry.getValue());
        }
        return next;
    }
}
