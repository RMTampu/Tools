package com.toolbox.tools.library;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PropertyContract {
    private final String propertyId;
    private final PropertyType type;
    private final boolean nullable;
    private final boolean editable;
    private final String defaultValue;
    private final Set<String> enumValues;
    private final Double minValue;
    private final Double maxValue;
    private final String unit;
    private final Set<String> stateApplicability;
    private final String converterId;

    /**
     * Backward-compatible constructor. Rich metadata can be supplied through
     * the extended constructor below.
     */
    public PropertyContract(
            String propertyId,
            PropertyType type,
            boolean nullable,
            boolean editable,
            String defaultValue,
            Set<String> enumValues
    ) {
        this(
                propertyId,
                type,
                nullable,
                editable,
                defaultValue,
                enumValues,
                null,
                null,
                null,
                Collections.emptySet(),
                null
        );
    }

    public PropertyContract(
            String propertyId,
            PropertyType type,
            boolean nullable,
            boolean editable,
            String defaultValue,
            Set<String> enumValues,
            Double minValue,
            Double maxValue,
            String unit,
            Set<String> stateApplicability,
            String converterId
    ) {
        this.propertyId = StableId.require(
                propertyId,
                "propertyId"
        );
        this.type = Objects.requireNonNull(type, "type");
        this.nullable = nullable;
        this.editable = editable;
        this.defaultValue = defaultValue;

        LinkedHashSet<String> choices = new LinkedHashSet<>();
        if (enumValues != null) {
            for (String value : enumValues) {
                if (value == null || value.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "enum property value invalid"
                    );
                }
                choices.add(value.trim());
            }
        }
        if (type == PropertyType.ENUM && choices.isEmpty()) {
            throw new IllegalArgumentException(
                    "enum property requires values"
            );
        }
        if (type != PropertyType.ENUM && !choices.isEmpty()) {
            throw new IllegalArgumentException(
                    "enum values only valid for ENUM property"
            );
        }
        this.enumValues = Collections.unmodifiableSet(choices);

        if (minValue != null && maxValue != null
                && minValue > maxValue) {
            throw new IllegalArgumentException(
                    "property range invalid"
            );
        }
        if ((minValue != null || maxValue != null)
                && type != PropertyType.NUMBER
                && type != PropertyType.DIMENSION) {
            throw new IllegalArgumentException(
                    "range only valid for numeric property"
            );
        }
        this.minValue = minValue;
        this.maxValue = maxValue;

        if (unit != null
                && !unit.matches("[A-Za-z%._-]{1,24}")) {
            throw new IllegalArgumentException(
                    "property unit invalid"
            );
        }
        this.unit = unit;

        LinkedHashSet<String> states = new LinkedHashSet<>();
        if (stateApplicability != null) {
            for (String state : stateApplicability) {
                states.add(
                        StableId.require(
                                state,
                                "propertyState"
                        )
                );
            }
        }
        this.stateApplicability =
                Collections.unmodifiableSet(states);
        this.converterId = converterId == null
                ? null
                : StableId.require(
                        converterId,
                        "converterId"
                );

        if (defaultValue == null && !nullable) {
            throw new IllegalArgumentException(
                    "non-null property requires default"
            );
        }
        if (defaultValue != null
                && !acceptsInternal(defaultValue, true)) {
            throw new IllegalArgumentException(
                    "property default violates contract"
            );
        }
    }

    public String propertyId() { return propertyId; }
    public PropertyType type() { return type; }
    public boolean nullable() { return nullable; }
    public boolean editable() { return editable; }
    public boolean readOnly() { return !editable; }
    public String defaultValue() { return defaultValue; }
    public Set<String> enumValues() { return enumValues; }
    public Double minValue() { return minValue; }
    public Double maxValue() { return maxValue; }
    public String unit() { return unit; }
    public Set<String> stateApplicability() {
        return stateApplicability;
    }
    public String converterId() { return converterId; }

    public boolean appliesToState(String stateId) {
        if (stateApplicability.isEmpty()) return true;
        if (stateId == null) return false;
        try {
            return stateApplicability.contains(
                    StableId.require(
                            stateId,
                            "propertyState"
                    )
            );
        } catch (RuntimeException error) {
            return false;
        }
    }

    public boolean accepts(String value) {
        return editable && acceptsInternal(value, false);
    }

    public String validationMessage(String value) {
        if (!editable) return "PROPERTY_READ_ONLY";
        if (acceptsInternal(value, false)) return "PASS";
        if (value == null || value.trim().isEmpty()) {
            return nullable
                    ? "PASS"
                    : "PROPERTY_REQUIRED";
        }
        return "PROPERTY_TYPE_OR_RANGE_INVALID";
    }

    private boolean acceptsInternal(
            String value,
            boolean allowReadOnlyDefault
    ) {
        if (!allowReadOnlyDefault && !editable) return false;
        if (value == null) return nullable;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return nullable;

        switch (type) {
            case BOOLEAN:
                return "true".equalsIgnoreCase(trimmed)
                        || "false".equalsIgnoreCase(trimmed);
            case NUMBER:
            case DIMENSION:
                try {
                    double number = Double.parseDouble(trimmed);
                    return Double.isFinite(number)
                            && (minValue == null
                                || number >= minValue)
                            && (maxValue == null
                                || number <= maxValue);
                } catch (NumberFormatException error) {
                    return false;
                }
            case COLOR:
                return trimmed.matches(
                        "#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"
                ) || trimmed.matches(
                        "[A-Za-z][A-Za-z0-9._-]{0,63}"
                );
            case ENUM:
                return enumValues.contains(trimmed);
            case URI:
                return trimmed.length() <= 2048
                        && (trimmed.startsWith("content://")
                            || trimmed.startsWith("https://")
                            || trimmed.startsWith("http://"));
            case ASSET:
            case REFERENCE:
                if (nullable && trimmed.isEmpty()) return true;
                try {
                    StableId.require(
                            trimmed,
                            type == PropertyType.ASSET
                                    ? "assetId"
                                    : "referenceId"
                    );
                    return true;
                } catch (RuntimeException error) {
                    return false;
                }
            case LIST:
            case OBJECT:
                return trimmed.length() <= 64 * 1024;
            case TEXT:
            default:
                return trimmed.length() <= 32 * 1024;
        }
    }

    @Override
    public String toString() {
        return propertyId
                + ":"
                + type.name().toLowerCase(Locale.ROOT)
                + (editable ? ":editable" : ":readonly");
    }
}
