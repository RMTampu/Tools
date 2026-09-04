package com.toolbox.tools.editor;

import java.util.Objects;

public final class FloatingEditorController {
    private final FloatingPlacementEngine placementEngine;
    private FloatingEditorState active;

    public FloatingEditorController(
            FloatingPlacementEngine placementEngine
    ) {
        this.placementEngine = Objects.requireNonNull(
                placementEngine,
                "placementEngine"
        );
    }

    public synchronized FloatingEditorState open(
            String editorId,
            String targetObjectId,
            EditorRect safeBounds,
            EditorRect selectedObject,
            int width,
            int height
    ) {
        EditorPoint position = placementEngine.place(
                safeBounds,
                selectedObject,
                width,
                height
        );
        active = new FloatingEditorState(
                editorId,
                targetObjectId,
                position,
                width,
                height,
                false
        );
        return active;
    }

    public synchronized FloatingEditorState drag(
            EditorPoint requested,
            EditorRect safeBounds
    ) {
        if (active == null) {
            throw new IllegalStateException("floating editor unavailable");
        }
        active = active.withPosition(
                placementEngine.clamp(
                        safeBounds,
                        requested,
                        active.width(),
                        active.height()
                )
        );
        return active;
    }

    public synchronized FloatingEditorState pin(boolean pinned) {
        if (active == null) {
            throw new IllegalStateException("floating editor unavailable");
        }
        active = active.withPinned(pinned);
        return active;
    }

    public synchronized void close() {
        active = null;
    }

    public synchronized FloatingEditorState active() {
        return active;
    }
}
