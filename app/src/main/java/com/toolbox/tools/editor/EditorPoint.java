package com.toolbox.tools.editor;

public final class EditorPoint {
    private final int x;
    private final int y;

    public EditorPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EditorPoint)) return false;
        EditorPoint that = (EditorPoint) other;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
