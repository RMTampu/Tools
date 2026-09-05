package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AppLifecycleManager {
    public enum Event {
        APP_START,
        FOREGROUND,
        BACKGROUND,
        SCREEN_ENTER,
        SCREEN_VISIBLE,
        SCREEN_LEAVE,
        SCREEN_RETURN
    }

    public enum Policy {
        EVERY_ENTER,
        FIRST_ENTER,
        WHEN_DATA_STALE
    }

    public static final class Entry {
        private final Event event;
        private final String screenId;
        private final long sequence;
        private final long timestampMs;

        Entry(
                Event event,
                String screenId,
                long sequence,
                long timestampMs
        ) {
            this.event = event;
            this.screenId = screenId;
            this.sequence = sequence;
            this.timestampMs = timestampMs;
        }

        public Event event() { return event; }
        public String screenId() { return screenId; }
        public long sequence() { return sequence; }
        public long timestampMs() { return timestampMs; }
    }

    public static final class LifecycleAction {
        private final String actionId;
        private final String screenId;
        private final Event event;
        private final Policy policy;
        private final long staleAfterMs;
        private long lastExecutedAt;
        private int executionCount;

        LifecycleAction(
                String actionId,
                String screenId,
                Event event,
                Policy policy,
                long staleAfterMs
        ) {
            this.actionId = actionId;
            this.screenId = screenId;
            this.event = event;
            this.policy = policy;
            this.staleAfterMs = staleAfterMs;
        }

        public String actionId() { return actionId; }
        public String screenId() { return screenId; }
        public Event event() { return event; }
        public Policy policy() { return policy; }
        public long staleAfterMs() { return staleAfterMs; }
        public long lastExecutedAt() { return lastExecutedAt; }
        public int executionCount() { return executionCount; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, LifecycleAction> actions =
            new LinkedHashMap<>();
    private long sequence;

    public synchronized void emit(
            Event event,
            String screenId
    ) {
        emit(event, screenId, System.currentTimeMillis());
    }

    public synchronized void emit(
            Event event,
            String screenId,
            long timestampMs
    ) {
        Objects.requireNonNull(event, "event");
        if (timestampMs < 0) {
            throw new IllegalArgumentException(
                    "timestamp lifecycle invalid"
            );
        }
        String screen = screenId == null
                ? null
                : StableId.require(screenId, "screenId");
        entries.add(new Entry(
                event,
                screen,
                ++sequence,
                timestampMs
        ));
        while (entries.size() > 128) {
            entries.remove(0);
        }
    }

    public synchronized void registerAction(
            String actionId,
            String screenId,
            Event event,
            Policy policy,
            long staleAfterMs
    ) {
        String id = StableId.require(
                actionId,
                "lifecycleActionId"
        );
        if (actions.containsKey(id)) {
            throw new IllegalArgumentException(
                    "lifecycle action duplicate"
            );
        }
        String screen = screenId == null
                ? null
                : StableId.require(screenId, "screenId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(policy, "policy");
        if (policy == Policy.WHEN_DATA_STALE
                && staleAfterMs < 1_000L) {
            throw new IllegalArgumentException(
                    "stale threshold terlalu kecil"
            );
        }
        if (policy != Policy.WHEN_DATA_STALE
                && staleAfterMs != 0L) {
            throw new IllegalArgumentException(
                    "stale threshold hanya untuk WHEN_DATA_STALE"
            );
        }
        actions.put(
                id,
                new LifecycleAction(
                        id,
                        screen,
                        event,
                        policy,
                        staleAfterMs
                )
        );
    }

    public synchronized boolean shouldRun(
            String actionId,
            Event event,
            String screenId,
            long nowMs
    ) {
        LifecycleAction action = requireAction(actionId);
        if (action.event() != event) return false;

        String normalizedScreen = screenId == null
                ? null
                : StableId.require(screenId, "screenId");
        if (action.screenId() != null
                && !action.screenId().equals(normalizedScreen)) {
            return false;
        }

        switch (action.policy()) {
            case EVERY_ENTER:
                return true;
            case FIRST_ENTER:
                return action.executionCount() == 0;
            case WHEN_DATA_STALE:
                return action.executionCount() == 0
                        || nowMs - action.lastExecutedAt()
                            >= action.staleAfterMs();
            default:
                return false;
        }
    }

    public synchronized void markExecuted(
            String actionId,
            long nowMs
    ) {
        if (nowMs < 0) {
            throw new IllegalArgumentException(
                    "timestamp lifecycle invalid"
            );
        }
        LifecycleAction action = requireAction(actionId);
        action.lastExecutedAt = nowMs;
        action.executionCount++;
    }

    public synchronized List<String> eligibleActions(
            Event event,
            String screenId,
            long nowMs
    ) {
        List<String> out = new ArrayList<>();
        for (LifecycleAction action : actions.values()) {
            if (shouldRun(
                    action.actionId(),
                    event,
                    screenId,
                    nowMs
            )) {
                out.add(action.actionId());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized LifecycleAction requireAction(
            String actionId
    ) {
        LifecycleAction action = actions.get(
                StableId.require(
                        actionId,
                        "lifecycleActionId"
                )
        );
        if (action == null) {
            throw new IllegalArgumentException(
                    "lifecycle action tidak tersedia"
            );
        }
        return action;
    }

    public synchronized List<Entry> history() {
        return Collections.unmodifiableList(
                new ArrayList<>(entries)
        );
    }

    public synchronized List<LifecycleAction> actions() {
        return Collections.unmodifiableList(
                new ArrayList<>(actions.values())
        );
    }

    public synchronized boolean completeContract() {
        for (Policy policy : Policy.values()) {
            boolean found = false;
            for (LifecycleAction action : actions.values()) {
                if (action.policy() == policy) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
