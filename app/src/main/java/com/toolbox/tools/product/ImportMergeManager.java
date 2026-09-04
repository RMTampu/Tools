package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImportMergeManager {
    public static final class Result {
        private final String projectId;
        private final Map<String, String> idMap;

        Result(String projectId, Map<String, String> idMap) {
            this.projectId = projectId;
            this.idMap = Collections.unmodifiableMap(idMap);
        }

        public String projectId() { return projectId; }
        public Map<String, String> idMap() { return idMap; }
    }

    private long sequence;

    public synchronized Result importAsNew(
            String projectId,
            Iterable<String> stableIds
    ) {
        String project = StableId.require(projectId, "projectId");
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String id : stableIds) {
            String stable = StableId.require(id, "stableId");
            map.put(stable, stable);
        }
        return new Result(project, map);
    }

    public synchronized Result mergeInto(
            String targetProjectId,
            Iterable<String> incomingIds,
            Iterable<String> existingIds
    ) {
        String project = StableId.require(targetProjectId, "projectId");
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        for (String id : existingIds) existing.add(StableId.require(id, "existingId"));

        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String id : incomingIds) {
            String stable = StableId.require(id, "incomingId");
            String mapped = stable;
            if (existing.contains(mapped) || map.containsValue(mapped)) {
                do {
                    mapped = stable + ".import." + (++sequence);
                } while (existing.contains(mapped) || map.containsValue(mapped));
            }
            map.put(stable, mapped);
        }
        return new Result(project, map);
    }
}
