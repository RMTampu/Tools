package com.toolbox.tools.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class LiveCompareResult {
    private final List<LiveChange> changes;
    private final String checksum;

    public LiveCompareResult(List<LiveChange> changes) {
        ArrayList<LiveChange> copy = new ArrayList<>(changes);
        copy.sort(Comparator.comparing(LiveChange::resourceId)
                .thenComparing(LiveChange::changeId));
        this.changes = Collections.unmodifiableList(copy);
        this.checksum = compute(copy);
    }

    public List<LiveChange> changes() { return changes; }
    public int changeCount() { return changes.size(); }
    public String checksum() { return checksum; }

    private static String compute(List<LiveChange> changes) {
        StringBuilder canonical = new StringBuilder("TBX_LIVE_COMPARE_V1\n");
        for (LiveChange change : changes) {
            canonical.append(change.changeId()).append('|')
                    .append(change.resourceId()).append('|')
                    .append(change.operation().name()).append('|')
                    .append(change.payload() == null ? "" : change.payload())
                    .append('\n');
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
