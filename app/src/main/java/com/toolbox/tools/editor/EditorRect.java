package com.toolbox.tools.editor;

public final class EditorRect {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public EditorRect(int left, int top, int right, int bottom) {
        if (right < left || bottom < top) {
            throw new IllegalArgumentException("invalid rect");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int left() { return left; }
    public int top() { return top; }
    public int right() { return right; }
    public int bottom() { return bottom; }
    public int width() { return right - left; }
    public int height() { return bottom - top; }

    public boolean intersects(EditorRect other) {
        return left < other.right
                && right > other.left
                && top < other.bottom
                && bottom > other.top;
    }

    public EditorPoint clampTopLeft(
            EditorPoint requested,
            int itemWidth,
            int itemHeight
    ) {
        if (itemWidth < 0 || itemHeight < 0) {
            throw new IllegalArgumentException("negative item size");
        }
        int maxX = Math.max(left, right - itemWidth);
        int maxY = Math.max(top, bottom - itemHeight);
        return new EditorPoint(
                Math.max(left, Math.min(requested.x(), maxX)),
                Math.max(top, Math.min(requested.y(), maxY))
        );
    }
}
