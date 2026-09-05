package com.toolbox.tools.delivery;

import com.toolbox.tools.core.ProjectState;

public interface PatchHealthGate {
    PatchHealthGate PROJECT_ONLY = state -> true;

    boolean isHealthy(ProjectState state);
}
