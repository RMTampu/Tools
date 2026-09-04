package com.toolbox.tools.repair;

import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.StableId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class RepairPlan {
    public static final int MAX_OPERATIONS = 128;

    private final String planId;
    private final String projectId;
    private final long baseRevision;
    private final Map<String, String> upserts;
    private final Set<String> deletes;
    private final String checksum;

    public RepairPlan(
            String planId,
            String projectId,
            long baseRevision,
            Map<String, String> upserts,
            Set<String> deletes
    ) {
        this.planId = StableId.require(planId, "planId");
        this.projectId = StableId.require(projectId, "projectId");
        if (baseRevision <= 0) {
            throw new IllegalArgumentException("repair baseRevision must be > 0");
        }
        this.baseRevision = baseRevision;
        if (upserts == null || deletes == null
                || upserts.size() + deletes.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("repair operation budget exceeded");
        }

        TreeMap<String, String> normalizedUpserts = new TreeMap<>();
        for (Map.Entry<String, String> entry : upserts.entrySet()) {
            String id = StableId.require(entry.getKey(), "resourceId");
            String payload = java.util.Objects.requireNonNull(
                    entry.getValue(),
                    "repair payload"
            );
            if (payload.getBytes(StandardCharsets.UTF_8).length
                    > ProjectState.MAX_RESOURCE_BYTES) {
                throw new IllegalArgumentException("repair resource too large");
            }
            normalizedUpserts.put(id, payload);
        }

        TreeSet<String> normalizedDeletes = new TreeSet<>();
        for (String id : deletes) {
            normalizedDeletes.add(StableId.require(id, "resourceId"));
        }

        for (String id : normalizedDeletes) {
            if (normalizedUpserts.containsKey(id)) {
                throw new IllegalArgumentException(
                        "repair cannot upsert and delete same resource"
                );
            }
        }

        this.upserts = Collections.unmodifiableMap(normalizedUpserts);
        this.deletes = Collections.unmodifiableSet(normalizedDeletes);
        this.checksum = computeChecksum();
    }

    public String planId() { return planId; }
    public String projectId() { return projectId; }
    public long baseRevision() { return baseRevision; }
    public Map<String, String> upserts() { return upserts; }
    public Set<String> deletes() { return deletes; }
    public String checksum() { return checksum; }

    private String computeChecksum() {
        StringBuilder canonical = new StringBuilder();
        canonical.append("TBX_REPAIR_V1\n")
                .append(planId).append('\n')
                .append(projectId).append('\n')
                .append(baseRevision).append('\n');
        for (Map.Entry<String, String> entry : upserts.entrySet()) {
            canonical.append("U|")
                    .append(entry.getKey()).append('|')
                    .append(entry.getValue().length()).append('|')
                    .append(entry.getValue()).append('\n');
        }
        for (String id : deletes) {
            canonical.append("D|").append(id).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder out = new StringBuilder();
            for (byte value : bytes) {
                out.append(String.format(
                        java.util.Locale.ROOT,
                        "%02x",
                        value
                ));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
