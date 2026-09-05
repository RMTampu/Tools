package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ThemeTokenManager {
    public enum Source {
        BASE,
        THEME,
        SCREEN,
        OBJECT,
        STATE
    }

    public static final class Resolution {
        private final String requestedTokenId;
        private final String resolvedTokenId;
        private final String value;
        private final Source source;

        Resolution(
                String requestedTokenId,
                String resolvedTokenId,
                String value,
                Source source
        ) {
            this.requestedTokenId = requestedTokenId;
            this.resolvedTokenId = resolvedTokenId;
            this.value = value;
            this.source = source;
        }

        public String requestedTokenId() { return requestedTokenId; }
        public String resolvedTokenId() { return resolvedTokenId; }
        public String value() { return value; }
        public Source source() { return source; }
    }

    private final Map<String, String> tokens = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();

    public ThemeTokenManager() {
        tokens.put("token.color.background", "#071016");
        tokens.put("token.color.surface", "#0D1B24");
        tokens.put("token.color.surface.alt", "#112832");
        tokens.put("token.color.neon", "#00F0B5");
        tokens.put("token.color.neon.blue", "#4CC9FF");
        tokens.put("token.color.text", "#E8FFF8");
        tokens.put("token.color.text.muted", "#8FB8AE");
        tokens.put("token.radius.card", "18");
        tokens.put("token.spacing.unit", "8");
    }

    public synchronized void set(String tokenId, String value) {
        String id = StableId.require(tokenId, "tokenId");
        tokens.put(id, Objects.requireNonNull(value, "value"));
        aliases.remove(id);
    }

    public synchronized String get(String tokenId) {
        Resolution resolution = resolve(
                tokenId,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
        return resolution == null ? null : resolution.value();
    }

    public synchronized Resolution resolve(
            String tokenId,
            Map<String, String> themeOverrides,
            Map<String, String> screenOverrides,
            Map<String, String> objectOverrides,
            Map<String, String> stateOverrides
    ) {
        String requested = StableId.require(tokenId, "tokenId");
        String resolved = followAlias(requested);

        Resolution override = override(
                requested,
                resolved,
                stateOverrides,
                Source.STATE
        );
        if (override != null) return override;
        override = override(
                requested,
                resolved,
                objectOverrides,
                Source.OBJECT
        );
        if (override != null) return override;
        override = override(
                requested,
                resolved,
                screenOverrides,
                Source.SCREEN
        );
        if (override != null) return override;
        override = override(
                requested,
                resolved,
                themeOverrides,
                Source.THEME
        );
        if (override != null) return override;

        String value = tokens.get(resolved);
        return value == null
                ? null
                : new Resolution(
                        requested,
                        resolved,
                        value,
                        Source.BASE
                );
    }

    public synchronized Set<String> brokenReferences(
            Set<String> referencedTokenIds
    ) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        if (referencedTokenIds == null) return missing;
        for (String id : referencedTokenIds) {
            String stable = StableId.require(id, "tokenId");
            if (get(stable) == null) missing.add(stable);
        }
        return Collections.unmodifiableSet(missing);
    }

    public synchronized void relink(
            String missingTokenId,
            String existingTokenId
    ) {
        String missing = StableId.require(
                missingTokenId,
                "missingTokenId"
        );
        String target = StableId.require(
                existingTokenId,
                "existingTokenId"
        );
        if (tokens.containsKey(missing)) {
            throw new IllegalArgumentException(
                    "token tidak hilang"
            );
        }
        String resolvedTarget = followAlias(target);
        if (!tokens.containsKey(resolvedTarget)) {
            throw new IllegalArgumentException(
                    "target relink tidak tersedia"
            );
        }
        aliases.put(missing, resolvedTarget);
        followAlias(missing);
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(tokens)
        );
    }

    public synchronized Map<String, String> relinks() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(aliases)
        );
    }

    private Resolution override(
            String requested,
            String resolved,
            Map<String, String> values,
            Source source
    ) {
        if (values == null || values.isEmpty()) return null;
        String value = values.containsKey(requested)
                ? values.get(requested)
                : values.get(resolved);
        return value == null
                ? null
                : new Resolution(
                        requested,
                        resolved,
                        value,
                        source
                );
    }

    private String followAlias(String id) {
        String current = id;
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        for (int guard = 0; guard < 32; guard++) {
            if (!visited.add(current)) {
                throw new IllegalStateException(
                        "token relink cycle"
                );
            }
            String next = aliases.get(current);
            if (next == null) return current;
            current = next;
        }
        throw new IllegalStateException(
                "token relink depth exceeded"
        );
    }
}
