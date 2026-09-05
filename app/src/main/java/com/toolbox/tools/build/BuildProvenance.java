package com.toolbox.tools.build;

import java.util.Objects;

public final class BuildProvenance {
    private final String sourceRepository;
    private final String sourceCommitSha;
    private final String sourceRef;
    private final String ciRepository;
    private final String ciWorkflowRef;
    private final String toolchainLock;
    private final String expectedSignerSha256;
    private final String baselineApkSha256;

    public BuildProvenance(
            String sourceRepository,
            String sourceCommitSha,
            String sourceRef,
            String ciRepository,
            String ciWorkflowRef,
            String toolchainLock,
            String expectedSignerSha256,
            String baselineApkSha256
    ) {
        this.sourceRepository = repository(
                sourceRepository,
                "sourceRepository"
        );
        this.sourceCommitSha = sha40(
                sourceCommitSha,
                "sourceCommitSha"
        );
        this.sourceRef = text(sourceRef, "sourceRef", 240);
        this.ciRepository = repository(
                ciRepository,
                "ciRepository"
        );
        this.ciWorkflowRef = text(
                ciWorkflowRef,
                "ciWorkflowRef",
                400
        );
        this.toolchainLock = text(
                toolchainLock,
                "toolchainLock",
                400
        );
        this.expectedSignerSha256 = sha256(
                expectedSignerSha256,
                "expectedSignerSha256"
        );
        this.baselineApkSha256 = sha256(
                baselineApkSha256,
                "baselineApkSha256"
        );
    }

    public String sourceRepository() { return sourceRepository; }
    public String sourceCommitSha() { return sourceCommitSha; }
    public String sourceRef() { return sourceRef; }
    public String ciRepository() { return ciRepository; }
    public String ciWorkflowRef() { return ciWorkflowRef; }
    public String toolchainLock() { return toolchainLock; }
    public String expectedSignerSha256() { return expectedSignerSha256; }
    public String baselineApkSha256() { return baselineApkSha256; }

    public boolean exactSourceBound() {
        return !sourceCommitSha.matches("0{40}");
    }

    public String canonical() {
        return "SOURCE_REPOSITORY=" + sourceRepository + "\n"
                + "SOURCE_COMMIT_SHA=" + sourceCommitSha + "\n"
                + "SOURCE_REF=" + sourceRef + "\n"
                + "CI_REPOSITORY=" + ciRepository + "\n"
                + "CI_WORKFLOW_REF=" + ciWorkflowRef + "\n"
                + "TOOLCHAIN_LOCK=" + toolchainLock + "\n"
                + "EXPECTED_SIGNER_SHA256=" + expectedSignerSha256 + "\n"
                + "BASELINE_APK_SHA256=" + baselineApkSha256 + "\n";
    }

    private static String repository(String value, String field) {
        String result = text(value, field, 200);
        if (!result.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(field + " invalid");
        }
        return result;
    }

    private static String sha40(String value, String field) {
        String result = Objects.requireNonNull(value, field)
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (!result.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(field + " invalid");
        }
        return result;
    }

    private static String sha256(String value, String field) {
        String result = Objects.requireNonNull(value, field)
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (!result.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " invalid");
        }
        return result;
    }

    private static String text(
            String value,
            String field,
            int max
    ) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty()
                || result.length() > max
                || result.indexOf('\n') >= 0
                || result.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " invalid");
        }
        return result;
    }
}
