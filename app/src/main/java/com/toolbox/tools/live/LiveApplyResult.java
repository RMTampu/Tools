package com.toolbox.tools.live;

public final class LiveApplyResult {
    private final boolean pass;
    private final LiveSessionState state;
    private final long revision;
    private final String message;

    public LiveApplyResult(
            boolean pass,
            LiveSessionState state,
            long revision,
            String message
    ) {
        this.pass = pass;
        this.state = state;
        this.revision = revision;
        this.message = message;
    }

    public boolean isPass() { return pass; }
    public LiveSessionState state() { return state; }
    public long revision() { return revision; }
    public String message() { return message; }
}
