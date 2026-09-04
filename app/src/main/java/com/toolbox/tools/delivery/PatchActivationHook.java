package com.toolbox.tools.delivery;

import com.toolbox.tools.core.ProjectState;

public interface PatchActivationHook {
    void onActivated(ProjectState state) throws Exception;

    PatchActivationHook NO_OP = state -> {};
}
