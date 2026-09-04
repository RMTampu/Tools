package com.toolbox.tools.editor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class BubblePositionStore {
    private final Map<Orientation, EditorPoint> positions =
            new EnumMap<>(Orientation.class);

    public synchronized EditorPoint get(
            Orientation orientation,
            EditorRect safeBounds,
            int bubbleSize
    ) {
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(safeBounds, "safeBounds");
        EditorPoint stored = positions.get(orientation);
        if (stored == null) {
            stored = new EditorPoint(
                    safeBounds.right() - bubbleSize,
                    safeBounds.top() + safeBounds.height() / 3
            );
        }
        return safeBounds.clampTopLeft(stored, bubbleSize, bubbleSize);
    }

    public synchronized EditorPoint save(
            Orientation orientation,
            EditorPoint requested,
            EditorRect safeBounds,
            int bubbleSize
    ) {
        EditorPoint clamped = safeBounds.clampTopLeft(
                Objects.requireNonNull(requested, "requested"),
                bubbleSize,
                bubbleSize
        );
        positions.put(
                Objects.requireNonNull(orientation, "orientation"),
                clamped
        );
        return clamped;
    }

    public synchronized void reset() {
        positions.clear();
    }
}
