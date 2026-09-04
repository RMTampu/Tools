package com.toolbox.tools.runtime;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RenderTree {
    private final String screenId;
    private final List<RenderNode> nodes;
    private final List<RuntimeDiagnostic> diagnostics;

    public RenderTree(
            String screenId,
            List<RenderNode> nodes,
            List<RuntimeDiagnostic> diagnostics
    ) {
        this.screenId = StableId.require(screenId, "screenId");
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.diagnostics = Collections.unmodifiableList(
                new ArrayList<>(diagnostics)
        );
    }

    public String screenId() { return screenId; }
    public List<RenderNode> nodes() { return nodes; }
    public List<RuntimeDiagnostic> diagnostics() { return diagnostics; }
}
