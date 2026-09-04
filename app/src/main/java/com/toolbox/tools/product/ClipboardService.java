package com.toolbox.tools.product;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClipboardService {
    public static final class Clip {
        private final String sourceId;
        private final Map<String, String> properties;

        Clip(String sourceId, Map<String, String> properties) {
            this.sourceId = sourceId;
            this.properties = Collections.unmodifiableMap(properties);
        }

        public String sourceId() { return sourceId; }
        public Map<String, String> properties() { return properties; }
    }

    private Clip current;
    private long sequence;

    public synchronized void copy(String sourceId, Map<String, String> properties) {
        String id = StableId.require(sourceId, "sourceId");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (properties != null) copy.putAll(properties);
        current = new Clip(id, copy);
    }

    public synchronized String pasteNewId(String prefix) {
        if (current == null) throw new IllegalStateException("clipboard kosong");
        String stablePrefix = StableId.require(prefix, "prefix");
        sequence++;
        return stablePrefix + ".copy." + sequence;
    }

    public synchronized Clip current() { return current; }
}
