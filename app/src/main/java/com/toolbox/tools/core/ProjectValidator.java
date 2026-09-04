package com.toolbox.tools.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectValidator {
    public ProjectValidationResult validate(ProjectState state) {
        List<String> errors = new ArrayList<>();
        if (state == null) {
            errors.add("PROJECT_MISSING");
            return ProjectValidationResult.of(errors);
        }
        if (state.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION) {
            errors.add("SCHEMA_INCOMPATIBLE");
        }
        if (state.buildModelVersion() != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            errors.add("BUILD_MODEL_INCOMPATIBLE");
        }
        for (Map.Entry<String, Set<String>> entry : state.references().entrySet()) {
            if (!state.resources().containsKey(entry.getKey())) {
                errors.add("REFERENCE_SOURCE_MISSING:" + entry.getKey());
            }
            for (String target : entry.getValue()) {
                if (!state.resources().containsKey(target)) {
                    errors.add("REFERENCE_TARGET_MISSING:" + target);
                }
            }
        }
        return ProjectValidationResult.of(errors);
    }
}
