package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;

public final class FloatingEditorState {
    private final String editorId;
    private final String targetObjectId;
    private final EditorPoint position;
    private final int width;
    private final int height;
    private final boolean pinned;

    public FloatingEditorState(
            String editorId,
            String targetObjectId,
            EditorPoint position,
            int width,
            int height,
            boolean pinned
    ) {
        this.editorId = StableId.require(editorId, "editorId");
        this.targetObjectId = StableId.require(targetObjectId, "targetObjectId");
        this.position = position;
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("floating editor size invalid");
        }
        this.width = width;
        this.height = height;
        this.pinned = pinned;
    }

    public String editorId() { return editorId; }
    public String targetObjectId() { return targetObjectId; }
    public EditorPoint position() { return position; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean pinned() { return pinned; }

    public FloatingEditorState withPosition(EditorPoint next) {
        return new FloatingEditorState(
                editorId,
                targetObjectId,
                next,
                width,
                height,
                pinned
        );
    }

    public FloatingEditorState withPinned(boolean next) {
        return new FloatingEditorState(
                editorId,
                targetObjectId,
                position,
                width,
                height,
                next
        );
    }
}
