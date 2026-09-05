package com.toolbox.tools.product;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ConditionalPropertyEngine {
    public boolean evaluate(String expression, Map<String, String> context) {
        if (expression == null || expression.trim().isEmpty()) return true;
        Objects.requireNonNull(context, "context");
        for (String orPart : expression.trim().split("\\s*\\|\\|\\s*")) {
            boolean and = true;
            for (String atom : orPart.split("\\s*&&\\s*")) {
                and &= evaluateAtom(atom.trim(), context);
            }
            if (and) return true;
        }
        return false;
    }

    public Map<String, String> apply(
            Map<String, String> base,
            Map<String, String> expressions,
            Map<String, String> context
    ) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>(base);
        for (Map.Entry<String, String> entry : expressions.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(".visible.if")) {
                out.put(key.substring(0, key.length() - 3), Boolean.toString(evaluate(entry.getValue(), context)));
            } else if (key.endsWith(".enabled.if")) {
                out.put(key.substring(0, key.length() - 3), Boolean.toString(evaluate(entry.getValue(), context)));
            } else if (key.endsWith(".selected.if")) {
                out.put(key.substring(0, key.length() - 3), Boolean.toString(evaluate(entry.getValue(), context)));
            }
        }
        return out;
    }

    private boolean evaluateAtom(String atom, Map<String, String> context) {
        if (atom.startsWith("!")) {
            return !truthy(context.get(atom.substring(1).trim()));
        }
        if (atom.contains("!=")) {
            String[] p = atom.split("!=", 2);
            return !Objects.equals(context.get(p[0].trim()), strip(p[1]));
        }
        if (atom.contains("==")) {
            String[] p = atom.split("==", 2);
            return Objects.equals(context.get(p[0].trim()), strip(p[1]));
        }
        return truthy(context.get(atom));
    }

    private static String strip(String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static boolean truthy(String value) {
        return value != null
                && ("true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value)
                || "aktif".equalsIgnoreCase(value));
    }
}
