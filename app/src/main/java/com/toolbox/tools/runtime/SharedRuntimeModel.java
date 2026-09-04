package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SharedRuntimeModel {
    private final Map<String, ScreenDefinition> screens;
    private final String startScreenId;

    public SharedRuntimeModel(
            Map<String, ScreenDefinition> screens,
            String startScreenId
    ) {
        LinkedHashMap<String, ScreenDefinition> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ScreenDefinition> entry :
                Objects.requireNonNull(screens, "screens").entrySet()) {
            String id = StableId.require(entry.getKey(), "screenId");
            ScreenDefinition screen = Objects.requireNonNull(
                    entry.getValue(),
                    "screen"
            );
            if (!id.equals(screen.screenId())) {
                throw new IllegalArgumentException("screen map identity mismatch");
            }
            if (copy.put(id, screen) != null) {
                throw new IllegalArgumentException("duplicate screen");
            }
        }
        this.startScreenId = StableId.require(startScreenId, "startScreenId");
        if (!copy.containsKey(this.startScreenId)) {
            throw new IllegalArgumentException("start screen unavailable");
        }
        this.screens = Collections.unmodifiableMap(copy);
    }

    public Map<String, ScreenDefinition> screens() { return screens; }
    public String startScreenId() { return startScreenId; }
    public ScreenDefinition screen(String screenId) {
        return screens.get(StableId.require(screenId, "screenId"));
    }
}
