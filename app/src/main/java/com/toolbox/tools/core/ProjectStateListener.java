package com.toolbox.tools.core;

import java.util.Set;

public interface ProjectStateListener {
    void onProjectStateChanged(
            ProjectState state,
            boolean committed,
            Set<String> touchedResourceIds
    );
}
