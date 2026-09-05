package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InputRouter {
    public enum Event {
        TAP, LONG_PRESS, DOUBLE_TAP, SWIPE, SCROLL, TEXT, KEYBOARD, MULTI_TOUCH, FOCUS
    }
    public enum Propagation { TARGET_ONLY, CONTINUE, CONSUME, STOP }

    public static final class Dispatch {
        private final Event event;
        private final List<String> capture;
        private final String target;
        private final List<String> bubble;
        private final Propagation propagation;

        Dispatch(
                Event event,
                List<String> capture,
                String target,
                List<String> bubble,
                Propagation propagation
        ) {
            this.event = event;
            this.capture = Collections.unmodifiableList(new ArrayList<>(capture));
            this.target = target;
            this.bubble = Collections.unmodifiableList(new ArrayList<>(bubble));
            this.propagation = propagation;
        }

        public Event event() { return event; }
        public List<String> capture() { return capture; }
        public String target() { return target; }
        public List<String> bubble() { return bubble; }
        public Propagation propagation() { return propagation; }
    }

    private final Map<String, String> parents = new LinkedHashMap<>();
    private final List<String> focusOrder = new ArrayList<>();

    public synchronized void register(String id, String parentId) {
        String stable = StableId.require(id, "inputNode");
        if (parentId != null) {
            String parent = StableId.require(parentId, "parentId");
            if (stable.equals(parent)) throw new IllegalArgumentException("input cycle");
            parents.put(stable, parent);
        } else {
            parents.put(stable, null);
        }
    }

    public synchronized Dispatch dispatch(
            String targetId,
            Event event,
            Propagation propagation
    ) {
        String target = StableId.require(targetId, "targetId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(propagation, "propagation");
        if (!parents.containsKey(target)) {
            throw new IllegalArgumentException("input target unavailable");
        }

        List<String> ancestors = new ArrayList<>();
        String current = parents.get(target);
        int guard = 0;
        while (current != null) {
            if (++guard > 128) throw new IllegalStateException("input parent cycle");
            ancestors.add(current);
            current = parents.get(current);
        }

        List<String> capture = new ArrayList<>();
        if (propagation != Propagation.TARGET_ONLY) {
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                capture.add(ancestors.get(i));
            }
        }

        List<String> bubble = new ArrayList<>();
        if (propagation == Propagation.CONTINUE) {
            bubble.addAll(ancestors);
        }

        return new Dispatch(event, capture, target, bubble, propagation);
    }

    public synchronized void setFocusOrder(List<String> ids) {
        focusOrder.clear();
        for (String id : ids) {
            String stable = StableId.require(id, "focusId");
            if (!parents.containsKey(stable)) {
                throw new IllegalArgumentException("focus target unavailable");
            }
            if (!focusOrder.contains(stable)) focusOrder.add(stable);
        }
    }

    public synchronized String nextFocus(String currentId) {
        if (focusOrder.isEmpty()) return null;
        int index = focusOrder.indexOf(currentId);
        if (index < 0) return focusOrder.get(0);
        return focusOrder.get((index + 1) % focusOrder.size());
    }

    public synchronized boolean complete() {
        return parents.containsKey("screen.home")
                && parents.containsKey("object.home.primary")
                && focusOrder.contains("object.home.primary");
    }
}
