package com.toolbox.tools.product;

import com.toolbox.tools.core.MemoryRuntimeStateStore;
import com.toolbox.tools.core.RuntimeStateStore;
import com.toolbox.tools.core.StableId;

import java.util.Objects;

public final class EditorContextStore {
    private static final String PREFIX = "editor.context.";

    private final RuntimeStateStore state;

    private String screenId = "screen.home";
    private String selectedObjectId;
    private String activeFunction = "UI";
    private String representation = "VISUAL";
    private String mode = "EDIT";
    private float zoom = 1f;
    private float panX;
    private float panY;
    private int scrollY;
    private boolean bubbleVisible = true;
    private boolean edgeOpen = true;
    private boolean floatingVisible;
    private String floatingAnchor = "CENTER";
    private String panelState = "ROOT";

    public EditorContextStore() {
        this(new MemoryRuntimeStateStore());
    }

    public EditorContextStore(RuntimeStateStore state) {
        this.state = Objects.requireNonNull(state, "state");
        load();
    }

    public synchronized void updateScreen(String id) {
        screenId = StableId.require(id, "screenId");
        persist();
    }

    public synchronized void select(String id) {
        selectedObjectId = id == null
                ? null
                : StableId.require(id, "objectId");
        persist();
    }

    public synchronized void setActiveFunction(String value) {
        if (value == null || !value.matches(
                "UI|LOGIC|DATA|BINDING|ASSET"
        )) {
            throw new IllegalArgumentException(
                    "fungsi editor invalid"
            );
        }
        activeFunction = value;
        persist();
    }

    public synchronized void setRepresentation(String value) {
        if (value == null || !value.matches(
                "VISUAL|PROPERTIES|CODE"
        )) {
            throw new IllegalArgumentException(
                    "representasi invalid"
            );
        }
        representation = value;
        persist();
    }

    public synchronized void setMode(String value) {
        if (value == null || !value.matches(
                "EDIT|PREVIEW|TEST|LIVE"
        )) {
            throw new IllegalArgumentException(
                    "mode editor invalid"
            );
        }
        mode = value;
        persist();
    }

    public synchronized void setViewport(
            float zoom,
            float panX,
            float panY,
            int scrollY
    ) {
        if (zoom < 0.25f || zoom > 4f
                || Float.isNaN(panX)
                || Float.isNaN(panY)
                || Float.isInfinite(panX)
                || Float.isInfinite(panY)) {
            throw new IllegalArgumentException(
                    "viewport invalid"
            );
        }
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
        this.scrollY = Math.max(0, scrollY);
        persist();
    }

    public synchronized void setShell(
            boolean bubbleVisible,
            boolean edgeOpen
    ) {
        this.bubbleVisible = bubbleVisible;
        this.edgeOpen = edgeOpen;
        persist();
    }

    public synchronized void setFloating(
            boolean visible,
            String anchor
    ) {
        if (anchor == null || !anchor.matches(
                "LEFT|RIGHT|TOP|BOTTOM|CENTER"
        )) {
            throw new IllegalArgumentException(
                    "floating anchor invalid"
            );
        }
        floatingVisible = visible;
        floatingAnchor = anchor;
        persist();
    }

    public synchronized void setPanelState(String stateValue) {
        if (stateValue == null || !stateValue.matches(
                "ROOT|FUNCTIONS|REPRESENTATION|MODES|CONTEXT"
        )) {
            throw new IllegalArgumentException(
                    "panel state invalid"
            );
        }
        panelState = stateValue;
        persist();
    }

    public synchronized void clamp(
            int viewportWidth,
            int viewportHeight
    ) {
        int width = Math.max(1, viewportWidth);
        int height = Math.max(1, viewportHeight);
        float maxPanX = width * 0.25f;
        float maxPanY = height * 0.25f;
        float minPanX = Math.min(
                -maxPanX,
                width - width * zoom
        );
        float minPanY = Math.min(
                -maxPanY,
                height - height * zoom
        );
        panX = clampFloat(
                panX,
                minPanX - maxPanX,
                maxPanX
        );
        panY = clampFloat(
                panY,
                minPanY - maxPanY,
                maxPanY
        );
        scrollY = Math.max(0, scrollY);
        if (!floatingAnchor.matches(
                "LEFT|RIGHT|TOP|BOTTOM|CENTER"
        )) {
            floatingAnchor = "CENTER";
        }
        persist();
    }

    public synchronized String screenId() { return screenId; }
    public synchronized String selectedObjectId() {
        return selectedObjectId;
    }
    public synchronized String activeFunction() {
        return activeFunction;
    }
    public synchronized String representation() {
        return representation;
    }
    public synchronized String mode() { return mode; }
    public synchronized float zoom() { return zoom; }
    public synchronized float panX() { return panX; }
    public synchronized float panY() { return panY; }
    public synchronized int scrollY() { return scrollY; }
    public synchronized boolean bubbleVisible() {
        return bubbleVisible;
    }
    public synchronized boolean edgeOpen() { return edgeOpen; }
    public synchronized boolean floatingVisible() {
        return floatingVisible;
    }
    public synchronized String floatingAnchor() {
        return floatingAnchor;
    }
    public synchronized String panelState() { return panelState; }

    public synchronized boolean completeContract() {
        return screenId != null
                && activeFunction != null
                && representation != null
                && mode != null
                && zoom >= 0.25f
                && zoom <= 4f
                && floatingAnchor != null
                && panelState != null;
    }

    private void load() {
        String screen = state.get(PREFIX + "screen");
        if (screen != null) {
            try {
                screenId = StableId.require(screen, "screenId");
            } catch (RuntimeException ignored) {}
        }
        String selected = state.get(PREFIX + "selection");
        if (selected != null && !selected.isEmpty()) {
            try {
                selectedObjectId = StableId.require(
                        selected,
                        "objectId"
                );
            } catch (RuntimeException ignored) {}
        }
        activeFunction = allowed(
                state.get(PREFIX + "function"),
                "UI",
                "UI|LOGIC|DATA|BINDING|ASSET"
        );
        representation = allowed(
                state.get(PREFIX + "representation"),
                "VISUAL",
                "VISUAL|PROPERTIES|CODE"
        );
        mode = allowed(
                state.get(PREFIX + "mode"),
                "EDIT",
                "EDIT|PREVIEW|TEST|LIVE"
        );
        zoom = parseFloat(
                state.get(PREFIX + "zoom"),
                1f,
                0.25f,
                4f
        );
        panX = parseFloat(
                state.get(PREFIX + "panX"),
                0f,
                -100000f,
                100000f
        );
        panY = parseFloat(
                state.get(PREFIX + "panY"),
                0f,
                -100000f,
                100000f
        );
        scrollY = Math.max(
                0,
                parseInt(state.get(PREFIX + "scrollY"), 0)
        );
        bubbleVisible = parseBoolean(
                state.get(PREFIX + "bubble"),
                true
        );
        edgeOpen = parseBoolean(
                state.get(PREFIX + "edge"),
                true
        );
        floatingVisible = parseBoolean(
                state.get(PREFIX + "floating.visible"),
                false
        );
        floatingAnchor = allowed(
                state.get(PREFIX + "floating.anchor"),
                "CENTER",
                "LEFT|RIGHT|TOP|BOTTOM|CENTER"
        );
        panelState = allowed(
                state.get(PREFIX + "panel"),
                "ROOT",
                "ROOT|FUNCTIONS|REPRESENTATION|MODES|CONTEXT"
        );
    }

    private void persist() {
        state.put(PREFIX + "screen", screenId);
        if (selectedObjectId == null) {
            state.remove(PREFIX + "selection");
        } else {
            state.put(PREFIX + "selection", selectedObjectId);
        }
        state.put(PREFIX + "function", activeFunction);
        state.put(PREFIX + "representation", representation);
        state.put(PREFIX + "mode", mode);
        state.put(PREFIX + "zoom", Float.toString(zoom));
        state.put(PREFIX + "panX", Float.toString(panX));
        state.put(PREFIX + "panY", Float.toString(panY));
        state.put(PREFIX + "scrollY", Integer.toString(scrollY));
        state.put(
                PREFIX + "bubble",
                Boolean.toString(bubbleVisible)
        );
        state.put(PREFIX + "edge", Boolean.toString(edgeOpen));
        state.put(
                PREFIX + "floating.visible",
                Boolean.toString(floatingVisible)
        );
        state.put(PREFIX + "floating.anchor", floatingAnchor);
        state.put(PREFIX + "panel", panelState);
    }

    private static String allowed(
            String value,
            String fallback,
            String regex
    ) {
        return value != null && value.matches(regex)
                ? value
                : fallback;
    }

    private static float parseFloat(
            String value,
            float fallback,
            float min,
            float max
    ) {
        if (value == null) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isNaN(parsed)
                    || Float.isInfinite(parsed)
                    || parsed < min
                    || parsed > max) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static int parseInt(
            String value,
            int fallback
    ) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static boolean parseBoolean(
            String value,
            boolean fallback
    ) {
        if (value == null) return fallback;
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        return fallback;
    }

    private static float clampFloat(
            float value,
            float min,
            float max
    ) {
        return Math.max(min, Math.min(max, value));
    }
}
