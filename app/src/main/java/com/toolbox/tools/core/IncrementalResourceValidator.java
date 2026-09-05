package com.toolbox.tools.core;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class IncrementalResourceValidator {
    public static final int MAX_VALUE_BYTES = 64 * 1024;

    public static final class Result {
        private final Set<String> checked;
        private final String diagnostic;

        Result(Set<String> checked, String diagnostic) {
            this.checked = Collections.unmodifiableSet(
                    new LinkedHashSet<>(checked)
            );
            this.diagnostic = diagnostic;
        }

        public boolean isPass() { return diagnostic == null; }
        public Set<String> checked() { return checked; }
        public String diagnostic() {
            return diagnostic == null ? "PASS" : diagnostic;
        }
    }

    public Result validate(
            ProjectState current,
            Map<String, String> upserts,
            Set<String> deletes
    ) {
        if (current == null || upserts == null || deletes == null) {
            return new Result(
                    Collections.emptySet(),
                    "INCREMENTAL_INPUT_MISSING"
            );
        }

        LinkedHashSet<String> checked = new LinkedHashSet<>();
        for (String id : deletes) {
            try {
                StableId.require(id, "resourceId");
            } catch (RuntimeException error) {
                return new Result(checked, "RESOURCE_ID_INVALID");
            }
            if (upserts.containsKey(id)) {
                return new Result(
                        checked,
                        "RESOURCE_UPSERT_DELETE_CONFLICT"
                );
            }
            checked.add(id);
        }

        for (Map.Entry<String, String> entry : upserts.entrySet()) {
            String id;
            try {
                id = StableId.require(entry.getKey(), "resourceId");
            } catch (RuntimeException error) {
                return new Result(checked, "RESOURCE_ID_INVALID");
            }
            checked.add(id);
            String value = entry.getValue();
            if (value == null) {
                return new Result(checked, "RESOURCE_VALUE_NULL");
            }
            if (value.getBytes(StandardCharsets.UTF_8).length
                    > MAX_VALUE_BYTES) {
                return new Result(
                        checked,
                        "RESOURCE_VALUE_BUDGET_EXCEEDED"
                );
            }

            if (id.endsWith(".opacity")) {
                try {
                    float opacity = Float.parseFloat(value);
                    if (opacity < 0f || opacity > 1f) {
                        return new Result(
                                checked,
                                "RESOURCE_OPACITY_RANGE"
                        );
                    }
                } catch (NumberFormatException error) {
                    return new Result(
                            checked,
                            "RESOURCE_OPACITY_TYPE"
                    );
                }
            }
            if (id.endsWith(".enabled")
                    && !"true".equalsIgnoreCase(value)
                    && !"false".equalsIgnoreCase(value)) {
                return new Result(
                        checked,
                        "RESOURCE_BOOLEAN_TYPE"
                );
            }
            if (id.endsWith(".uri")
                    && !value.startsWith("content://")) {
                return new Result(
                        checked,
                        "RESOURCE_URI_NOT_CONTENT"
                );
            }
        }

        return new Result(checked, null);
    }
}
