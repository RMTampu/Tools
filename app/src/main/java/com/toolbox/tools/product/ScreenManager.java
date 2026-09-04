package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScreenManager {
    public static final class ScreenItem {
        private final String id;
        private final String name;
        private final boolean start;

        public ScreenItem(String id, String name, boolean start) {
            this.id = StableId.require(id, "screenId");
            String label = Objects.requireNonNull(name, "name").trim();
            if (label.isEmpty()) throw new IllegalArgumentException("nama layar kosong");
            this.name = label;
            this.start = start;
        }

        public String id() { return id; }
        public String name() { return name; }
        public boolean isStart() { return start; }

        ScreenItem withName(String value) {
            return new ScreenItem(id, value, start);
        }

        ScreenItem withStart(boolean value) {
            return new ScreenItem(id, name, value);
        }
    }

    private final LinkedHashMap<String, ScreenItem> screens = new LinkedHashMap<>();

    public ScreenManager() {
        screens.put("screen.home", new ScreenItem("screen.home", "Beranda", true));
        screens.put("screen.detail", new ScreenItem("screen.detail", "Detail", false));
    }

    public synchronized void add(String id, String name) {
        String stable = StableId.require(id, "screenId");
        if (screens.containsKey(stable)) {
            throw new IllegalArgumentException("layar sudah ada");
        }
        screens.put(stable, new ScreenItem(stable, name, screens.isEmpty()));
    }

    public synchronized void rename(String id, String name) {
        ScreenItem item = require(id);
        screens.put(item.id(), item.withName(name));
    }

    public synchronized void setStart(String id) {
        String target = StableId.require(id, "screenId");
        require(target);
        LinkedHashMap<String, ScreenItem> next = new LinkedHashMap<>();
        for (ScreenItem item : screens.values()) {
            next.put(item.id(), item.withStart(item.id().equals(target)));
        }
        screens.clear();
        screens.putAll(next);
    }

    public synchronized void delete(String id) {
        ScreenItem item = require(id);
        if (item.isStart() || screens.size() <= 1) {
            throw new IllegalStateException("layar awal tidak boleh dihapus");
        }
        screens.remove(item.id());
    }

    public synchronized void move(String id, int newIndex) {
        ScreenItem item = require(id);
        if (newIndex < 0 || newIndex >= screens.size()) {
            throw new IllegalArgumentException("posisi layar tidak valid");
        }
        List<ScreenItem> list = new ArrayList<>(screens.values());
        list.remove(item);
        list.add(newIndex, item);
        screens.clear();
        for (ScreenItem value : list) screens.put(value.id(), value);
    }

    public synchronized List<ScreenItem> all() {
        return Collections.unmodifiableList(new ArrayList<>(screens.values()));
    }

    public synchronized String startScreenId() {
        for (ScreenItem item : screens.values()) if (item.isStart()) return item.id();
        throw new IllegalStateException("layar awal tidak tersedia");
    }

    private ScreenItem require(String id) {
        ScreenItem item = screens.get(StableId.require(id, "screenId"));
        if (item == null) throw new IllegalArgumentException("layar tidak tersedia");
        return item;
    }
}
