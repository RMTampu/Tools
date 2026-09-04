package com.toolbox.tools.editor;

import java.util.Objects;

public final class BubbleController {
    private final BubblePositionStore positionStore;
    private boolean panelOpen;

    public BubbleController(BubblePositionStore positionStore) {
        this.positionStore = Objects.requireNonNull(
                positionStore,
                "positionStore"
        );
    }

    public synchronized boolean tap() {
        panelOpen = !panelOpen;
        return panelOpen;
    }

    public synchronized EditorPoint drag(
            Orientation orientation,
            EditorPoint requested,
            EditorRect safeBounds,
            int bubbleSize
    ) {
        return positionStore.save(
                orientation,
                requested,
                safeBounds,
                bubbleSize
        );
    }

    public synchronized EditorPoint position(
            Orientation orientation,
            EditorRect safeBounds,
            int bubbleSize
    ) {
        return positionStore.get(orientation, safeBounds, bubbleSize);
    }

    public synchronized boolean panelOpen() {
        return panelOpen;
    }

    public synchronized void reset() {
        panelOpen = false;
        positionStore.reset();
    }
}
