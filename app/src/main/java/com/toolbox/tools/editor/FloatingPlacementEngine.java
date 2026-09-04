package com.toolbox.tools.editor;

import java.util.Objects;

public final class FloatingPlacementEngine {
    private static final int GAP = 16;

    public EditorPoint place(
            EditorRect safeBounds,
            EditorRect selectedObject,
            int editorWidth,
            int editorHeight
    ) {
        Objects.requireNonNull(safeBounds, "safeBounds");
        Objects.requireNonNull(selectedObject, "selectedObject");

        EditorPoint right = safeBounds.clampTopLeft(
                new EditorPoint(selectedObject.right() + GAP, selectedObject.top()),
                editorWidth,
                editorHeight
        );
        EditorRect rightRect = rect(right, editorWidth, editorHeight);
        if (!rightRect.intersects(selectedObject)) {
            return right;
        }

        EditorPoint left = safeBounds.clampTopLeft(
                new EditorPoint(selectedObject.left() - GAP - editorWidth, selectedObject.top()),
                editorWidth,
                editorHeight
        );
        EditorRect leftRect = rect(left, editorWidth, editorHeight);
        if (!leftRect.intersects(selectedObject)) {
            return left;
        }

        EditorPoint below = safeBounds.clampTopLeft(
                new EditorPoint(selectedObject.left(), selectedObject.bottom() + GAP),
                editorWidth,
                editorHeight
        );
        EditorRect belowRect = rect(below, editorWidth, editorHeight);
        if (!belowRect.intersects(selectedObject)) {
            return below;
        }

        return safeBounds.clampTopLeft(
                new EditorPoint(safeBounds.left(), safeBounds.top()),
                editorWidth,
                editorHeight
        );
    }

    public EditorPoint clamp(
            EditorRect safeBounds,
            EditorPoint requested,
            int editorWidth,
            int editorHeight
    ) {
        return safeBounds.clampTopLeft(
                requested,
                editorWidth,
                editorHeight
        );
    }

    private static EditorRect rect(
            EditorPoint point,
            int width,
            int height
    ) {
        return new EditorRect(
                point.x(),
                point.y(),
                point.x() + width,
                point.y() + height
        );
    }
}
