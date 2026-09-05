package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import com.toolbox.tools.core.ProjectState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectGraphManager {
    private final Set<String> entities = new LinkedHashSet<>();
    private final Map<String, Set<String>> outgoing = new LinkedHashMap<>();
    private final Set<String> tombstones = new LinkedHashSet<>();
    private final Set<String> everUsedIds = new LinkedHashSet<>();
    private final Deque<String> undoDelete = new ArrayDeque<>();

    public synchronized void registerEntity(String id) {
        String stable = StableId.require(id, "entityId");
        if (entities.contains(stable)) return;
        if (everUsedIds.contains(stable)) {
            throw new IllegalArgumentException(
                    "Stable ID tidak boleh didaur ulang"
            );
        }
        entities.add(stable);
        everUsedIds.add(stable);
    }

    public synchronized void link(String fromId, String toId) {
        String from = StableId.require(fromId, "fromId");
        String to = StableId.require(toId, "toId");
        if (!entities.contains(from) || !entities.contains(to)) {
            throw new IllegalArgumentException("referensi hanya boleh ke Stable ID terdaftar");
        }
        outgoing.computeIfAbsent(from, ignored -> new LinkedHashSet<>()).add(to);
    }

    public synchronized void rebuildFrom(ProjectState project) {
        if (project == null) throw new NullPointerException("project");
        entities.clear();
        outgoing.clear();

        for (String id : project.resources().keySet()) {
            String stable = StableId.require(id, "resourceId");
            entities.add(stable);
            everUsedIds.add(stable);
        }
        for (Map.Entry<String, Set<String>> entry
                : project.references().entrySet()) {
            String source = StableId.require(
                    entry.getKey(),
                    "referenceSource"
            );
            entities.add(source);
            everUsedIds.add(source);
            for (String target : entry.getValue()) {
                String stableTarget = StableId.require(
                        target,
                        "referenceTarget"
                );
                entities.add(stableTarget);
                everUsedIds.add(stableTarget);
            }
        }
        for (Map.Entry<String, Set<String>> entry
                : project.references().entrySet()) {
            LinkedHashSet<String> targets = new LinkedHashSet<>();
            for (String target : entry.getValue()) {
                targets.add(target);
            }
            if (!targets.isEmpty()) {
                outgoing.put(
                        entry.getKey(),
                        targets
                );
            }
        }
    }

    public synchronized Set<String> impactOf(String id) {
        String root = StableId.require(id, "entityId");
        LinkedHashSet<String> impacted = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, Set<String>> entry : outgoing.entrySet()) {
                if (entry.getValue().contains(root)
                        || intersects(entry.getValue(), impacted)) {
                    if (impacted.add(entry.getKey())) changed = true;
                }
            }
        } while (changed);
        impacted.remove(root);
        return Collections.unmodifiableSet(impacted);
    }

    public synchronized void delete(String id) {
        String stable = StableId.require(id, "entityId");
        if (!entities.remove(stable)) {
            throw new IllegalArgumentException("entitas tidak tersedia");
        }
        tombstones.add(stable);
        undoDelete.addLast(stable);
    }

    public synchronized boolean undoDelete() {
        if (undoDelete.isEmpty()) return false;
        String id = undoDelete.removeLast();
        tombstones.remove(id);
        entities.add(id);
        return true;
    }

    public synchronized void compactTombstones(Set<String> stillReferencedByHistory) {
        Set<String> keep = stillReferencedByHistory == null
                ? Collections.emptySet()
                : stillReferencedByHistory;
        tombstones.removeIf(id -> !keep.contains(id));
    }

    public synchronized boolean isBrokenReference(String fromId, String toId) {
        StableId.require(fromId, "fromId");
        String to = StableId.require(toId, "toId");
        return !entities.contains(to);
    }

    public synchronized Map<String, Set<String>> generatedIndex() {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : outgoing.entrySet()) {
            copy.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue()))
            );
        }
        return Collections.unmodifiableMap(copy);
    }

    public synchronized List<String> tombstones() {
        return Collections.unmodifiableList(new ArrayList<>(tombstones));
    }

    public synchronized boolean wasEverUsed(String id) {
        return everUsedIds.contains(
                StableId.require(id, "entityId")
        );
    }

    public synchronized boolean canAllocate(String id) {
        String stable = StableId.require(id, "entityId");
        return !entities.contains(stable)
                && !everUsedIds.contains(stable);
    }

    public synchronized Set<String> everUsedIds() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(everUsedIds)
        );
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        for (String item : a) if (b.contains(item)) return true;
        return false;
    }
}
