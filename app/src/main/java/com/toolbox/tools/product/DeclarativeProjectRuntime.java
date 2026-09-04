package com.toolbox.tools.product;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.StableId;
import com.toolbox.tools.delivery.PatchPayload;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DeclarativeProjectRuntime {
    private static final int MAX_ENTRY_BYTES = 262_144;

    private Map<String, String> resources = Collections.emptyMap();
    private long revision;

    public DeclarativeProjectRuntime(ProjectState initial) {
        reload(initial);
    }

    public synchronized void reload(ProjectState state) {
        if (state == null) throw new NullPointerException("state");
        LinkedHashMap<String, String> next = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : state.resources().entrySet()) {
            validateResource(entry.getKey(), entry.getValue());
            next.put(entry.getKey(), entry.getValue());
        }
        resources = Collections.unmodifiableMap(next);
        revision = state.revision();
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized String value(String id, String fallback) {
        String key = StableId.require(id, "resourceId");
        String value = resources.get(key);
        return value == null ? fallback : value;
    }

    public synchronized Map<String, String> snapshot() {
        return resources;
    }

    public synchronized int countPrefix(String prefix) {
        int count = 0;
        for (String id : resources.keySet()) {
            if (id.startsWith(prefix)) count++;
        }
        return count;
    }

    public Set<AuthoringSection> validatePatch(PatchPayload payload) {
        if (payload == null) throw new NullPointerException("payload");
        EnumSet<AuthoringSection> affected =
                EnumSet.noneOf(AuthoringSection.class);
        for (Map.Entry<String, String> entry : payload.upserts().entrySet()) {
            validateResource(entry.getKey(), entry.getValue());
            affected.add(section(entry.getKey()));
        }
        for (String id : payload.deletes()) {
            validateId(id);
            affected.add(section(id));
        }
        return Collections.unmodifiableSet(affected);
    }

    public boolean supportsWithoutRebuild(String resourceId) {
        try {
            section(StableId.require(resourceId, "resourceId"));
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void validateResource(String id, String value) {
        validateId(id);
        if (value == null) throw new IllegalArgumentException("nilai resource kosong");
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException("resource melewati budget");
        }
        section(id);
    }

    private static void validateId(String id) {
        StableId.require(id, "resourceId");
    }

    private static AuthoringSection section(String id) {
        if (id.startsWith("ui.")
                || id.startsWith("screen.")
                || id.startsWith("component.")
                || id.startsWith("template.")
                || id.startsWith("theme.")
                || id.startsWith("token.")) {
            return AuthoringSection.UI;
        }
        if (id.startsWith("logic.") || id.startsWith("flow.")) {
            return AuthoringSection.LOGIC;
        }
        if (id.startsWith("data.")) {
            return AuthoringSection.DATA;
        }
        if (id.startsWith("binding.")) {
            return AuthoringSection.BINDING;
        }
        if (id.startsWith("asset.")) {
            return AuthoringSection.ASSET;
        }
        if (id.startsWith("app.") || id.startsWith("config.")) {
            return AuthoringSection.UI;
        }
        throw new IllegalArgumentException(
                "resource tidak didukung tanpa rebuild: " + id
        );
    }
}
