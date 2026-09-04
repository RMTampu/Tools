package com.toolbox.tools.build;

import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ApplicationIr {
    public static final int CURRENT_IR_VERSION = 1;
    public static final int MAX_CANONICAL_BYTES = 2 * 1024 * 1024;

    private final int irVersion;
    private final String projectId;
    private final long projectRevision;
    private final String canonical;
    private final String sha256;
    private final Map<String, Integer> counts;

    public ApplicationIr(
            int irVersion,
            String projectId,
            long projectRevision,
            String canonical,
            String sha256,
            Map<String, Integer> counts
    ) {
        if (irVersion != CURRENT_IR_VERSION) {
            throw new IllegalArgumentException("unsupported IR version");
        }
        this.irVersion = irVersion;
        this.projectId = StableId.require(projectId, "projectId");
        if (projectRevision < 0) {
            throw new IllegalArgumentException("IR revision invalid");
        }
        this.projectRevision = projectRevision;
        this.canonical = Objects.requireNonNull(
                canonical,
                "canonical"
        );
        if (canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("IR exceeds size budget");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("IR sha256 invalid");
        }
        this.sha256 = sha256;
        this.counts = Collections.unmodifiableMap(
                new LinkedHashMap<>(counts)
        );
    }

    public int irVersion() { return irVersion; }
    public String projectId() { return projectId; }
    public long projectRevision() { return projectRevision; }
    public String canonical() { return canonical; }
    public String sha256() { return sha256; }
    public Map<String, Integer> counts() { return counts; }
}
