package com.toolbox.tools.authoring;

import com.toolbox.tools.core.StableId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AuthoringDraft {
    private final String draftId;
    private final AuthoringSection section;
    private final String targetId;
    private final long revision;
    private final DraftLifecycle lifecycle;
    private final Map<String, String> fields;

    public AuthoringDraft(
            String draftId,
            AuthoringSection section,
            String targetId,
            long revision,
            DraftLifecycle lifecycle,
            Map<String, String> fields
    ) {
        this.draftId = StableId.require(draftId, "draftId");
        this.section = Objects.requireNonNull(section, "section");
        this.targetId = StableId.require(targetId, "targetId");
        if (revision < 0) {
            throw new IllegalArgumentException("draft revision invalid");
        }
        this.revision = revision;
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                copy.put(
                        StableId.require(entry.getKey(), "fieldId"),
                        Objects.requireNonNull(entry.getValue(), "field value")
                );
            }
        }
        this.fields = Collections.unmodifiableMap(copy);
    }

    public String draftId() { return draftId; }
    public AuthoringSection section() { return section; }
    public String targetId() { return targetId; }
    public long revision() { return revision; }
    public DraftLifecycle lifecycle() { return lifecycle; }
    public Map<String, String> fields() { return fields; }
}
