package com.toolbox.tools.product;

import com.toolbox.tools.core.VisibleWorkspaceStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

public final class AssetIntegrityVerifier {
    private static final long DEFAULT_MAX_BYTES =
            128L * 1024L * 1024L;

    public boolean verify(
            VisibleWorkspaceStore visible,
            VisibleWorkspaceStore.Area area,
            String storageName,
            String expectedSha256
    ) throws IOException {
        return verify(
                visible,
                area,
                storageName,
                expectedSha256,
                DEFAULT_MAX_BYTES
        );
    }

    public boolean verify(
            VisibleWorkspaceStore visible,
            VisibleWorkspaceStore.Area area,
            String storageName,
            String expectedSha256,
            long maxBytes
    ) throws IOException {
        Objects.requireNonNull(visible, "visible");
        Objects.requireNonNull(area, "area");
        if (storageName == null || storageName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "asset storage name missing"
            );
        }
        if (expectedSha256 == null
                || !expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "asset expected sha256 invalid"
            );
        }
        if (maxBytes <= 0 || maxBytes > DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "asset verify budget invalid"
            );
        }

        MessageDigest digest = digest();
        long total = 0;
        try (InputStream input = visible.openInputStream(
                area,
                storageName
        )) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(
                            "asset runtime budget exceeded"
                    );
                }
                digest.update(buffer, 0, read);
            }
        }
        return constantEquals(
                expectedSha256,
                hex(digest.digest())
        );
    }

    public String sha256(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        return hex(digest().digest(bytes));
    }

    private static MessageDigest digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
    }

    private static boolean constantEquals(
            String expected,
            String actual
    ) {
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format(
                    Locale.ROOT,
                    "%02x",
                    value
            ));
        }
        return out.toString();
    }
}
