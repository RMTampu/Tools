package com.toolbox.tools.authoring;

import com.toolbox.tools.core.StableId;
import java.util.Objects;

public final class AuthoringSearchResult {
    private final AuthoringItemKind kind;
    private final String stableId;
    private final String labelIndonesia;
    private final String version;

    public AuthoringSearchResult(
            AuthoringItemKind kind,
            String stableId,
            String labelIndonesia,
            String version
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.stableId = StableId.require(stableId, "stableId");
        String label = Objects.requireNonNull(
                labelIndonesia,
                "labelIndonesia"
        ).trim();
        if (label.isEmpty() || label.length() > 160) {
            throw new IllegalArgumentException("search label invalid");
        }
        this.labelIndonesia = label;
        this.version = version;
    }

    public AuthoringItemKind kind() { return kind; }
    public String stableId() { return stableId; }
    public String labelIndonesia() { return labelIndonesia; }
    public String version() { return version; }

    public String stableKey() {
        return kind.name() + ":" + stableId + ":" + (version == null ? "" : version);
    }
}
