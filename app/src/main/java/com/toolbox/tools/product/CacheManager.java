package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CacheManager {
    public enum Priority { HOT, WARM, COLD, TEMP }
    public enum Category {
        THUMBNAIL,
        PREVIEW,
        RENDER_TEMP,
        PARSER_INDEX,
        OTHER
    }
    public enum Tier { MEMORY, DISK }

    private static final class Entry {
        final long bytes;
        final Priority priority;
        final Category category;
        final Tier tier;
        final Runnable disposer;

        Entry(
                long bytes,
                Priority priority,
                Category category,
                Tier tier,
                Runnable disposer
        ) {
            this.bytes = bytes;
            this.priority = priority;
            this.category = category;
            this.tier = tier;
            this.disposer = disposer;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final EnumMap<Category, Long> categoryBudgets =
            new EnumMap<>(Category.class);
    private long memoryBudgetBytes = 48L * 1024L * 1024L;
    private long diskBudgetBytes = 96L * 1024L * 1024L;

    public CacheManager() {
        categoryBudgets.put(Category.THUMBNAIL, 16L * 1024L * 1024L);
        categoryBudgets.put(Category.PREVIEW, 48L * 1024L * 1024L);
        categoryBudgets.put(Category.RENDER_TEMP, 16L * 1024L * 1024L);
        categoryBudgets.put(Category.PARSER_INDEX, 16L * 1024L * 1024L);
        categoryBudgets.put(Category.OTHER, 32L * 1024L * 1024L);
    }

    /**
     * Backward-compatible global pressure knob. It controls memory cache;
     * disk cache keeps an independent budget as required by the design.
     */
    public synchronized void setBudgetBytes(long value) {
        setTierBudgetBytes(Tier.MEMORY, value);
    }

    public synchronized void setTierBudgetBytes(
            Tier tier,
            long value
    ) {
        if (tier == null) throw new NullPointerException("tier");
        if (value < 8L * 1024L * 1024L) {
            throw new IllegalArgumentException(
                    "budget cache terlalu kecil"
            );
        }
        if (tier == Tier.MEMORY) {
            memoryBudgetBytes = value;
        } else {
            diskBudgetBytes = value;
        }
        trimToBudgets();
    }

    public synchronized long tierBudgetBytes(Tier tier) {
        if (tier == null) throw new NullPointerException("tier");
        return tier == Tier.MEMORY
                ? memoryBudgetBytes
                : diskBudgetBytes;
    }

    public synchronized void setCategoryBudgetBytes(
            Category category,
            long value
    ) {
        if (category == null) {
            throw new NullPointerException("category");
        }
        if (value < 1L * 1024L * 1024L) {
            throw new IllegalArgumentException(
                    "budget kategori terlalu kecil"
            );
        }
        categoryBudgets.put(category, value);
        trimToBudgets();
    }

    public synchronized long categoryBudgetBytes(
            Category category
    ) {
        Long value = categoryBudgets.get(
                java.util.Objects.requireNonNull(
                        category,
                        "category"
                )
        );
        return value == null ? 0 : value;
    }

    public synchronized void put(
            String key,
            long bytes,
            Priority priority
    ) {
        put(
                key,
                bytes,
                priority,
                Category.OTHER,
                Tier.MEMORY,
                null
        );
    }

    public synchronized void put(
            String key,
            long bytes,
            Priority priority,
            Category category,
            Tier tier
    ) {
        put(key, bytes, priority, category, tier, null);
    }

    public synchronized void put(
            String key,
            long bytes,
            Priority priority,
            Category category,
            Tier tier,
            Runnable disposer
    ) {
        String id = StableId.require(key, "cacheKey");
        if (bytes < 0) {
            throw new IllegalArgumentException(
                    "ukuran cache tidak valid"
            );
        }
        if (priority == null
                || category == null
                || tier == null) {
            throw new NullPointerException(
                    "cache contract incomplete"
            );
        }
        Entry replaced = entries.put(
                id,
                new Entry(
                        bytes,
                        priority,
                        category,
                        tier,
                        disposer
                )
        );
        if (replaced != null && replaced.disposer != null) {
            safeDispose(replaced.disposer);
        }
        trimToBudgets();
    }

    public synchronized long totalBytes() {
        long total = 0;
        for (Entry item : entries.values()) total += item.bytes;
        return total;
    }

    public synchronized long totalBytes(Tier tier) {
        long total = 0;
        for (Entry item : entries.values()) {
            if (item.tier == tier) total += item.bytes;
        }
        return total;
    }

    public synchronized long bytesByCategory(
            Category category
    ) {
        long total = 0;
        for (Entry item : entries.values()) {
            if (item.category == category) total += item.bytes;
        }
        return total;
    }

    public synchronized Map<Category, Long> categorySizes() {
        EnumMap<Category, Long> out =
                new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            out.put(category, bytesByCategory(category));
        }
        return Collections.unmodifiableMap(out);
    }

    public synchronized int clearCategory(
            Category category
    ) {
        if (category == null) {
            throw new NullPointerException("category");
        }
        int removed = 0;
        java.util.Iterator<Map.Entry<String, Entry>>
                iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.category != category) continue;
            if (entry.disposer != null) {
                safeDispose(entry.disposer);
            }
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public synchronized int clearDisposable() {
        int removed = 0;
        java.util.Iterator<Map.Entry<String, Entry>>
                iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.priority != Priority.TEMP
                    && entry.priority != Priority.COLD) {
                continue;
            }
            if (entry.disposer != null) {
                safeDispose(entry.disposer);
            }
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public synchronized Map<String, Long> snapshot() {
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> item
                : entries.entrySet()) {
            out.put(item.getKey(), item.getValue().bytes);
        }
        return Collections.unmodifiableMap(out);
    }

    private void trimToBudgets() {
        for (Category category : Category.values()) {
            while (bytesByCategory(category)
                    > categoryBudgetBytes(category)) {
                if (!evictOne(category, null)) break;
            }
        }
        while (totalBytes(Tier.MEMORY) > memoryBudgetBytes) {
            if (!evictOne(null, Tier.MEMORY)) break;
        }
        while (totalBytes(Tier.DISK) > diskBudgetBytes) {
            if (!evictOne(null, Tier.DISK)) break;
        }
    }

    private boolean evictOne(
            Category category,
            Tier tier
    ) {
        String victim = null;
        Priority victimPriority = null;
        for (Map.Entry<String, Entry> item
                : entries.entrySet()) {
            Entry entry = item.getValue();
            if (entry.priority == Priority.HOT) continue;
            if (category != null
                    && entry.category != category) {
                continue;
            }
            if (tier != null && entry.tier != tier) {
                continue;
            }
            if (victim == null
                    || entry.priority.ordinal()
                    > victimPriority.ordinal()) {
                victim = item.getKey();
                victimPriority = entry.priority;
            }
        }
        if (victim == null) return false;
        Entry removed = entries.remove(victim);
        if (removed != null && removed.disposer != null) {
            safeDispose(removed.disposer);
        }
        return true;
    }

    private static void safeDispose(Runnable disposer) {
        try {
            disposer.run();
        } catch (RuntimeException ignored) {
            // Cache is disposable. Failing to delete one derived item must
            // never damage Project Store/original asset/recovery.
        }
    }
}
