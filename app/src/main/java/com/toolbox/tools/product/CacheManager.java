package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CacheManager {
    public enum Priority { HOT, WARM, COLD, TEMP }

    private static final class Entry {
        final long bytes;
        final Priority priority;
        Entry(long bytes, Priority priority) {
            this.bytes = bytes;
            this.priority = priority;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long budgetBytes = 64L * 1024L * 1024L;

    public synchronized void setBudgetBytes(long value) {
        if (value < 8L * 1024L * 1024L) {
            throw new IllegalArgumentException("budget cache terlalu kecil");
        }
        budgetBytes = value;
        trimToBudget();
    }

    public synchronized void put(String key, long bytes, Priority priority) {
        String id = StableId.require(key, "cacheKey");
        if (bytes < 0) throw new IllegalArgumentException("ukuran cache tidak valid");
        if (priority == null) throw new NullPointerException("priority");
        entries.put(id, new Entry(bytes, priority));
        trimToBudget();
    }

    public synchronized long totalBytes() {
        long total = 0;
        for (Entry item : entries.values()) total += item.bytes;
        return total;
    }

    public synchronized int clearDisposable() {
        int before = entries.size();
        entries.entrySet().removeIf(e ->
                e.getValue().priority == Priority.TEMP
                        || e.getValue().priority == Priority.COLD
        );
        return before - entries.size();
    }

    public synchronized Map<String, Long> snapshot() {
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            out.put(item.getKey(), item.getValue().bytes);
        }
        return Collections.unmodifiableMap(out);
    }

    private void trimToBudget() {
        while (totalBytes() > budgetBytes) {
            String victim = null;
            Priority victimPriority = null;
            for (Map.Entry<String, Entry> item : entries.entrySet()) {
                if (item.getValue().priority == Priority.HOT) continue;
                if (victim == null
                        || item.getValue().priority.ordinal()
                        > victimPriority.ordinal()) {
                    victim = item.getKey();
                    victimPriority = item.getValue().priority;
                }
            }
            if (victim == null) return;
            entries.remove(victim);
        }
    }
}
