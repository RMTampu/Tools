package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

public final class EditorContextStore {
    private String screenId = "screen.home";
    private String selectedObjectId;
    private String activeFunction = "UI";
    private float zoom = 1f;
    private float panX;
    private float panY;
    private int scrollY;
    private boolean bubbleVisible = true;
    private boolean edgeOpen = true;

    public synchronized void updateScreen(String id) {
        screenId = StableId.require(id, "screenId");
    }

    public synchronized void select(String id) {
        selectedObjectId = id == null ? null : StableId.require(id, "objectId");
    }

    public synchronized void setActiveFunction(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("fungsi kosong");
        activeFunction = value;
    }

    public synchronized void setViewport(float zoom, float panX, float panY, int scrollY) {
        if (zoom < 0.25f || zoom > 4f) throw new IllegalArgumentException("zoom di luar batas");
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
        this.scrollY = Math.max(0, scrollY);
    }

    public synchronized void setShell(boolean bubbleVisible, boolean edgeOpen) {
        this.bubbleVisible = bubbleVisible;
        this.edgeOpen = edgeOpen;
    }

    public synchronized String screenId() { return screenId; }
    public synchronized String selectedObjectId() { return selectedObjectId; }
    public synchronized String activeFunction() { return activeFunction; }
    public synchronized float zoom() { return zoom; }
    public synchronized float panX() { return panX; }
    public synchronized float panY() { return panY; }
    public synchronized int scrollY() { return scrollY; }
    public synchronized boolean bubbleVisible() { return bubbleVisible; }
    public synchronized boolean edgeOpen() { return edgeOpen; }
}
