package com.toolbox.tools.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HealthReport {
    private final HealthState state;
    private final List<String> reasons;

    public HealthReport(HealthState state, List<String> reasons) {
        this.state = state;
        this.reasons = Collections.unmodifiableList(
                new ArrayList<>(reasons)
        );
    }

    public HealthState state() { return state; }
    public List<String> reasons() { return reasons; }
    public boolean isHealthy() { return state == HealthState.HEALTHY; }
}
