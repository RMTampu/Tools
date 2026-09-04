package com.toolbox.tools.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class DigestUtils {
    private DigestUtils() {
    }

    public static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(value);
            StringBuilder out = new StringBuilder(result.length * 2);
            for (byte item : result) {
                out.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
