package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static final class Entry {
        private final Event event;
        private final String screenId;
        private final long sequence;

        Entry(Event event, String screenId, long sequence) {
            this.event = event;
            this.screenId = screenId;
            this.sequence = sequence;
        }

        public Event event() { return event; }
        public String screenId() { return screenId; }
        public long sequence() { return sequence; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private long sequence;

    public synchronized void emit(Event event, String screenId) {
        if (event == null) throw new NullPointerException("event");
        String screen = screenId == null ? null : StableId.require(screenId, "screenId");
        entries.add(new Entry(event, screen, ++sequence));
        while (entries.size() > 128) entries.remove(0);
    }

    public synchronized List<Entry> history() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
