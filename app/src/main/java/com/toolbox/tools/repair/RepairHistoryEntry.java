package com.toolbox.tools.repair;

public final class RepairHistoryEntry {
    private final String planId;
    private final RepairPhase phase;
    private final long revision;

    public RepairHistoryEntry(
            String planId,
            RepairPhase phase,
            long revision
    ) {
        this.planId = planId;
        this.phase = phase;
        this.revision = revision;
    }

    public String planId() { return planId; }
    public RepairPhase phase() { return phase; }
    public long revision() { return revision; }
}
