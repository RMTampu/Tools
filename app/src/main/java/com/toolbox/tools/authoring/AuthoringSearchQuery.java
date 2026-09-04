package com.toolbox.tools.authoring;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class AuthoringSearchQuery {
    public static final int MAX_QUERY_LENGTH = 128;
    public static final int MAX_RESULTS = 100;

    private final String text;
    private final AuthoringSection section;
    private final Set<AuthoringItemKind> kinds;
    private final int limit;

    public AuthoringSearchQuery(
            String text,
            AuthoringSection section,
            Set<AuthoringItemKind> kinds,
            int limit
    ) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("search query too long");
        }
        if (limit <= 0 || limit > MAX_RESULTS) {
            throw new IllegalArgumentException("search result limit invalid");
        }
        this.text = normalized;
        this.section = section;
        this.kinds = kinds == null || kinds.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(kinds));
        this.limit = limit;
    }

    public String text() { return text; }
    public AuthoringSection section() { return section; }
    public Set<AuthoringItemKind> kinds() { return kinds; }
    public int limit() { return limit; }

    public boolean accepts(AuthoringItemKind kind) {
        if (!kinds.isEmpty() && !kinds.contains(kind)) return false;
        if (section == null) return true;
        switch (section) {
            case UI:
                return kind == AuthoringItemKind.COMPONENT
                        || kind == AuthoringItemKind.TEMPLATE
                        || kind == AuthoringItemKind.SCREEN;
            case LOGIC:
                return kind == AuthoringItemKind.FLOW
                        || kind == AuthoringItemKind.ACTION
                        || kind == AuthoringItemKind.EVENT;
            case DATA:
                return kind == AuthoringItemKind.DATA_SOURCE;
            case BINDING:
                return kind == AuthoringItemKind.BINDING;
            case ASSET:
                return kind == AuthoringItemKind.ASSET
                        || kind == AuthoringItemKind.TEMPLATE;
            default:
                return false;
        }
    }
}
