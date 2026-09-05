package com.toolbox.tools.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ProjectChangeSet {
    private final Map<String, String> beforeValues;
    private final Set<String> beforeMissing;
    private final Map<String, String> afterValues;
    private final Set<String> afterMissing;
    private final Map<String, Set<String>> beforeReferences;
    private final Map<String, Set<String>> afterReferences;

    ProjectChangeSet(
            Map<String, String> beforeValues,
            Set<String> beforeMissing,
            Map<String, String> afterValues,
            Set<String> afterMissing,
            Map<String, Set<String>> beforeReferences,
            Map<String, Set<String>> afterReferences
    ) {
        this.beforeValues = Collections.unmodifiableMap(
                new LinkedHashMap<>(beforeValues)
        );
        this.beforeMissing = Collections.unmodifiableSet(
                new LinkedHashSet<>(beforeMissing)
        );
        this.afterValues = Collections.unmodifiableMap(
                new LinkedHashMap<>(afterValues)
        );
        this.afterMissing = Collections.unmodifiableSet(
                new LinkedHashSet<>(afterMissing)
        );
        this.beforeReferences = immutableReferences(beforeReferences);
        this.afterReferences = immutableReferences(afterReferences);
    }

    ProjectState applyForward(ProjectState state) {
        return apply(
                state,
                afterValues,
                afterMissing,
                afterReferences
        );
    }

    ProjectState applyReverse(ProjectState state) {
        return apply(
                state,
                beforeValues,
                beforeMissing,
                beforeReferences
        );
    }

    Set<String> referencedResourceIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.addAll(beforeValues.keySet());
        out.addAll(afterValues.keySet());
        out.addAll(beforeMissing);
        out.addAll(afterMissing);
        return Collections.unmodifiableSet(out);
    }

    private static ProjectState apply(
            ProjectState state,
            Map<String, String> values,
            Set<String> missing,
            Map<String, Set<String>> references
    ) {
        ProjectState next = state;
        for (String id : missing) {
            next = next.withoutResource(id);
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            next = next.withResource(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        LinkedHashSet<String> affectedSources =
                new LinkedHashSet<>(references.keySet());
        for (String id : referencedResourceIds(values, missing)) {
            affectedSources.add(id);
        }

        for (String source : affectedSources) {
            Set<String> currentTargets =
                    next.references().get(source);
            if (currentTargets != null) {
                for (String target
                        : new LinkedHashSet<>(currentTargets)) {
                    next = next.withoutReference(source, target);
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry
                : references.entrySet()) {
            String source = entry.getKey();
            if (!next.resources().containsKey(source)) {
                continue;
            }
            for (String target : entry.getValue()) {
                if (next.resources().containsKey(target)) {
                    next = next.withReference(source, target);
                }
            }
        }
        return next;
    }

    private static Set<String> referencedResourceIds(
            Map<String, String> values,
            Set<String> missing
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.addAll(values.keySet());
        out.addAll(missing);
        return out;
    }

    private static Map<String, Set<String>> immutableReferences(
            Map<String, Set<String>> input
    ) {
        LinkedHashMap<String, Set<String>> out =
                new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry
                : input.entrySet()) {
            out.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(
                            new LinkedHashSet<>(entry.getValue())
                    )
            );
        }
        return Collections.unmodifiableMap(out);
    }
}
