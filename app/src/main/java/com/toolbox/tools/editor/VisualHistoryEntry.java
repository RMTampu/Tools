package com.toolbox.tools.editor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class VisualHistoryEntry {
    private final String transactionId;
    private final Map<String, VisualObjectState> before;
    private final Map<String, VisualObjectState> after;

    VisualHistoryEntry(
            String transactionId,
            Map<String, VisualObjectState> before,
            Map<String, VisualObjectState> after
    ) {
        this.transactionId = transactionId;
        this.before = immutable(before);
        this.after = immutable(after);
    }

    String transactionId() { return transactionId; }
    Map<String, VisualObjectState> before() { return before; }
    Map<String, VisualObjectState> after() { return after; }

    private static Map<String, VisualObjectState> immutable(
            Map<String, VisualObjectState> input
    ) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(input)
        );
    }
}
