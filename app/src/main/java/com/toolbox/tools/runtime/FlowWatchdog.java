package com.toolbox.tools.runtime;

public final class FlowWatchdog {
    public static final int MAX_STEPS = 50_000;
    public static final long MAX_RUNTIME_MILLIS = 300_000L;

    private final long startedAtMillis;
    private int steps;

    public FlowWatchdog(long startedAtMillis) {
        if (startedAtMillis < 0) {
            throw new IllegalArgumentException("start time invalid");
        }
        this.startedAtMillis = startedAtMillis;
    }

    public synchronized void step(long nowMillis) {
        steps++;
        if (steps > MAX_STEPS) {
            throw new IllegalStateException("FLOW_LIMIT_EXCEEDED:steps");
        }
        if (nowMillis < startedAtMillis
                || nowMillis - startedAtMillis > MAX_RUNTIME_MILLIS) {
            throw new IllegalStateException("FLOW_LIMIT_EXCEEDED:time");
        }
    }

    public synchronized int steps() { return steps; }
}
