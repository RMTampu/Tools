package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VisualLayoutEngine {
    public enum LayoutMode { ROW, COLUMN, STACK, GRID, FREE }
    public enum AdaptiveClass { COMPACT, MEDIUM, EXPANDED }
    public enum Orientation { PORTRAIT, LANDSCAPE }
    public enum PointerBehavior { AUTO, NONE }

    public static final class Node {
        private final String id;
        private final String parentId;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final int z;
        private final boolean locked;
        private final PointerBehavior pointerBehavior;

        public Node(
                String id,
                String parentId,
                float x,
                float y,
                float width,
                float height,
                int z,
                boolean locked,
                PointerBehavior pointerBehavior
        ) {
            this.id = StableId.require(id, "nodeId");
            this.parentId = parentId == null ? null : StableId.require(parentId, "parentId");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("ukuran node tidak valid");
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.z = z;
            this.locked = locked;
            this.pointerBehavior = Objects.requireNonNull(pointerBehavior, "pointerBehavior");
        }

        public String id() { return id; }
        public String parentId() { return parentId; }
        public float x() { return x; }
        public float y() { return y; }
        public float width() { return width; }
        public float height() { return height; }
        public int z() { return z; }
        public boolean locked() { return locked; }
        public PointerBehavior pointerBehavior() { return pointerBehavior; }

        Node move(float nx, float ny) {
            return new Node(id,parentId,nx,ny,width,height,z,locked,pointerBehavior);
        }

        Node resize(float w, float h) {
            return new Node(id,parentId,x,y,w,h,z,locked,pointerBehavior);
        }

        Node reparent(String parent, float nx, float ny) {
            return new Node(id,parent,nx,ny,width,height,z,locked,pointerBehavior);
        }

        Node withZ(int next) {
            return new Node(id,parentId,x,y,width,height,next,locked,pointerBehavior);
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private float zoom = 1f;
    private float panX;
    private float panY;

    public synchronized void add(Node node) {
        Objects.requireNonNull(node, "node");
        if (nodes.containsKey(node.id())) throw new IllegalArgumentException("node duplikat");
        if (node.parentId() != null && !nodes.containsKey(node.parentId())) {
            throw new IllegalArgumentException("parent tidak tersedia");
        }
        nodes.put(node.id(), node);
    }

    public synchronized void move(String id, float x, float y, float gridSize) {
        Node node = requireEditable(id);
        nodes.put(node.id(), node.move(snap(x, gridSize), snap(y, gridSize)));
    }

    public synchronized void resize(String id, float width, float height, float gridSize) {
        Node node = requireEditable(id);
        nodes.put(node.id(), node.resize(
                Math.max(gridSize, snap(width, gridSize)),
                Math.max(gridSize, snap(height, gridSize))
        ));
    }

    public synchronized void reparent(String id, String parentId, float x, float y) {
        Node node = requireEditable(id);
        String parent = StableId.require(parentId, "parentId");
        if (!nodes.containsKey(parent)) throw new IllegalArgumentException("parent tidak tersedia");
        if (parent.equals(node.id()) || isDescendant(parent, node.id())) {
            throw new IllegalArgumentException("reparent membentuk siklus");
        }
        nodes.put(node.id(), node.reparent(parent, x, y));
    }

    public synchronized void groupMove(List<String> ids, float dx, float dy) {
        if (ids == null || ids.isEmpty()) return;
        LinkedHashMap<String, Node> next = new LinkedHashMap<>(nodes);
        for (String id : ids) {
            Node node = requireEditable(id);
            next.put(node.id(), node.move(node.x() + dx, node.y() + dy));
        }
        nodes.clear();
        nodes.putAll(next);
    }

    public synchronized Node hitTest(float x, float y) {
        List<Node> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparingInt(Node::z).reversed());
        for (Node node : ordered) {
            if (node.pointerBehavior() == PointerBehavior.NONE) continue;
            if (x >= node.x() && x <= node.x() + node.width()
                    && y >= node.y() && y <= node.y() + node.height()) {
                return node;
            }
        }
        return null;
    }

    public synchronized void bringToFront(String id) {
        Node node = requireEditable(id);
        int max = 0;
        for (Node item : nodes.values()) max = Math.max(max, item.z());
        nodes.put(id, node.withZ(max + 1));
    }

    public synchronized void setViewport(float zoom, float panX, float panY) {
        if (zoom < 0.25f || zoom > 4f) throw new IllegalArgumentException("zoom di luar batas");
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
    }

    public synchronized float designX(float editorX) {
        return (editorX - panX) / zoom;
    }

    public synchronized float designY(float editorY) {
        return (editorY - panY) / zoom;
    }

    public synchronized Node clampToSafeArea(
            String id,
            float left,
            float top,
            float right,
            float bottom
    ) {
        Node n = requireEditable(id);
        float nx = Math.max(left, Math.min(right - n.width(), n.x()));
        float ny = Math.max(top, Math.min(bottom - n.height(), n.y()));
        Node clamped = n.move(nx, ny);
        nodes.put(id, clamped);
        return clamped;
    }

    public synchronized AdaptiveClass adaptiveClass(float widthDp) {
        if (widthDp < 600f) return AdaptiveClass.COMPACT;
        if (widthDp < 840f) return AdaptiveClass.MEDIUM;
        return AdaptiveClass.EXPANDED;
    }

    public synchronized Map<String, Node> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    private Node requireEditable(String id) {
        Node n = nodes.get(StableId.require(id, "nodeId"));
        if (n == null) throw new IllegalArgumentException("node tidak tersedia");
        if (n.locked()) throw new IllegalStateException("node terkunci");
        return n;
    }

    private boolean isDescendant(String candidate, String ancestor) {
        Node current = nodes.get(candidate);
        while (current != null && current.parentId() != null) {
            if (ancestor.equals(current.parentId())) return true;
            current = nodes.get(current.parentId());
        }
        return false;
    }

    private static float snap(float value, float grid) {
        if (grid <= 0) return value;
        return Math.round(value / grid) * grid;
    }
}
