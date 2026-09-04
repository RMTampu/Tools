package com.toolbox.tools.build;

import com.toolbox.tools.core.StableId;

public final class CandidateIdentity {
    private final String candidateId;
    private final String sha256;
    private final String applicationId;
    private final int versionCode;
    private final String versionName;
    private final String parentSignedApkSha256;
    private final String irSha256;
    private final String unsignedApkSha256;

    public CandidateIdentity(
            String candidateId,
            String sha256,
            String applicationId,
            int versionCode,
            String versionName,
            String parentSignedApkSha256,
            String irSha256,
            String unsignedApkSha256
    ) {
        this.candidateId = StableId.require(
                candidateId,
                "candidateId"
        );
        requireSha256(sha256, "candidate sha256");
        requireSha256(
                parentSignedApkSha256,
                "parent signed apk sha256"
        );
        requireSha256(irSha256, "IR sha256");
        requireSha256(unsignedApkSha256, "unsigned apk sha256");
        if (versionCode <= 0) {
            throw new IllegalArgumentException("versionCode invalid");
        }
        this.sha256 = sha256;
        this.applicationId = StableId.require(
                applicationId,
                "applicationId"
        );
        this.versionCode = versionCode;
        this.versionName = java.util.Objects.requireNonNull(
                versionName,
                "versionName"
        );
        this.parentSignedApkSha256 = parentSignedApkSha256;
        this.irSha256 = irSha256;
        this.unsignedApkSha256 = unsignedApkSha256;
    }

    public String candidateId() { return candidateId; }
    public String sha256() { return sha256; }
    public String applicationId() { return applicationId; }
    public int versionCode() { return versionCode; }
    public String versionName() { return versionName; }
    public String parentSignedApkSha256() {
        return parentSignedApkSha256;
    }
    public String irSha256() { return irSha256; }
    public String unsignedApkSha256() {
        return unsignedApkSha256;
    }

    private static void requireSha256(
            String value,
            String label
    ) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " invalid");
        }
    }
}
