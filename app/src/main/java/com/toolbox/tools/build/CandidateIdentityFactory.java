package com.toolbox.tools.build;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class CandidateIdentityFactory {
    public CandidateIdentity create(
            String applicationId,
            int versionCode,
            String versionName,
            String parentSignedApkSha256,
            String irSha256,
            String unsignedApkSha256
    ) {
        String canonical = "TBX_CANDIDATE_V1\n"
                + applicationId + "\n"
                + versionCode + "\n"
                + versionName + "\n"
                + parentSignedApkSha256 + "\n"
                + irSha256 + "\n"
                + unsignedApkSha256 + "\n";
        String digest = sha256(canonical);
        return new CandidateIdentity(
                "candidate." + digest,
                digest,
                applicationId,
                versionCode,
                versionName,
                parentSignedApkSha256,
                irSha256,
                unsignedApkSha256
        );
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );
            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder out = new StringBuilder();
            for (byte item : bytes) {
                out.append(String.format(
                        java.util.Locale.ROOT,
                        "%02x",
                        item
                ));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
