package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ThemeTokenManager {
    private final Map<String, String> tokens = new LinkedHashMap<>();

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
        tokens.put(
                StableId.require(tokenId, "tokenId"),
                Objects.requireNonNull(value, "value")
        );
    }

    public synchronized String get(String tokenId) {
        return tokens.get(StableId.require(tokenId, "tokenId"));
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
    }
}
