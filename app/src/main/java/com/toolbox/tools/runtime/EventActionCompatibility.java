package com.toolbox.tools.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EventActionCompatibility {
    public static final class Result {
        private final boolean compatible;
        private final Map<String, String> converterIds;
        private final String reason;

        Result(
                boolean compatible,
                Map<String, String> converterIds,
                String reason
        ) {
            this.compatible = compatible;
            this.converterIds = Collections.unmodifiableMap(
                    new LinkedHashMap<>(converterIds)
            );
            this.reason = reason;
        }

        public boolean compatible() { return compatible; }
        public Map<String, String> converterIds() {
            return converterIds;
        }
        public String reason() { return reason; }
    }

    private final ValueConverterRegistry converters;

    public EventActionCompatibility() {
        this(ValueConverterRegistry.defaults());
    }

    public EventActionCompatibility(
            ValueConverterRegistry converters
    ) {
        this.converters = Objects.requireNonNull(
                converters,
                "converters"
        );
    }

    public boolean isCompatible(
            EventDefinition event,
            ActionContract action
    ) {
        return match(event, action).compatible();
    }

    public Result match(
            EventDefinition event,
            ActionContract action
    ) {
        if (event == null || action == null) {
            return new Result(
                    false,
                    Collections.emptyMap(),
                    "EVENT_OR_ACTION_MISSING"
            );
        }

        LinkedHashMap<String, String> plan =
                new LinkedHashMap<>();
        for (Map.Entry<String, ValueType> input
                : action.inputs().entrySet()) {
            ValueType available =
                    event.payload().get(input.getKey());
            if (available == null) {
                return new Result(
                        false,
                        plan,
                        "ACTION_INPUT_MISSING:"
                                + input.getKey()
                );
            }
            if (available == input.getValue()) {
                continue;
            }
            ValueConverterRegistry.Entry converter =
                    converters.resolve(
                            available,
                            input.getValue()
                    );
            if (converter == null) {
                return new Result(
                        false,
                        plan,
                        "NO_EXPLICIT_CONVERTER:"
                                + input.getKey()
                );
            }
            plan.put(
                    input.getKey(),
                    converter.converterId()
            );
        }
        return new Result(true, plan, "PASS");
    }
}
