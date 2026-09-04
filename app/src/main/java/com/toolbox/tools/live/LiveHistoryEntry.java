package com.toolbox.tools.live;

public final class LiveHistoryEntry {
    private final String sessionId;
    private final LiveSessionState state;
    private final long revision;
    private final String compareChecksum;

    public LiveHistoryEntry(
            String sessionId,
            LiveSessionState state,
            long revision,
            String compareChecksum
    ) {
        this.sessionId = sessionId;
        this.state = state;
        this.revision = revision;
        this.compareChecksum = compareChecksum;
    }

    public String sessionId() { return sessionId; }
    public LiveSessionState state() { return state; }
    public long revision() { return revision; }
    public String compareChecksum() { return compareChecksum; }
}
