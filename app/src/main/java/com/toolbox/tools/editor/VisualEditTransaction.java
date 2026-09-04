package com.toolbox.tools.editor;

import com.toolbox.tools.core.StableId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VisualEditTransaction {
    private final String transactionId;
    private final List<VisualEditOperation> operations;

    public VisualEditTransaction(
            String transactionId,
            List<VisualEditOperation> operations
    ) {
        this.transactionId = StableId.require(transactionId, "transactionId");
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("edit transaction empty");
        }
        this.operations = Collections.unmodifiableList(
                new ArrayList<>(operations)
        );
    }

    public String transactionId() { return transactionId; }
    public List<VisualEditOperation> operations() { return operations; }
}
