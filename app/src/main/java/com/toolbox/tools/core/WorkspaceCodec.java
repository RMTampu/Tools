package com.toolbox.tools.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class WorkspaceCodec {
    static final String HEADER = "TBX_STAGE2_V1";

    public String encode(WorkspaceSnapshot snapshot) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        StringBuilder body = new StringBuilder();
        body.append(HEADER).append('\n');
        body.append("id=").append(encodeText(snapshot.workspaceId())).append('\n');
        body.append("schema=").append(snapshot.schemaVersion()).append('\n');
        body.append("revision=").append(snapshot.revision()).append('\n');
        body.append("size=").append(snapshot.values().size()).append('\n');

        for (Map.Entry<String, String> entry : new TreeMap<>(snapshot.values()).entrySet()) {
            body.append("e.")
                    .append(encodeText(entry.getKey()))
                    .append('=')
                    .append(encodeText(entry.getValue()))
                    .append('\n');
        }

        String checksum = sha256(body.toString().getBytes(StandardCharsets.UTF_8));
        return body.append("sha256=").append(checksum).append('\n').toString();
    }

    public WorkspaceSnapshot decode(String encoded) {
        if (encoded == null || encoded.length() > 1_048_576) {
            throw new IllegalArgumentException("workspace payload invalid");
        }
        if (!encoded.endsWith("\n")) {
            throw new IllegalArgumentException("workspace payload must end with newline");
        }

        int checksumStart = encoded.lastIndexOf("sha256=");
        if (checksumStart <= 0 || encoded.indexOf("sha256=") != checksumStart) {
            throw new IllegalArgumentException("workspace checksum missing or duplicated");
        }

        String body = encoded.substring(0, checksumStart);
        String checksumLine = encoded.substring(checksumStart, encoded.length() - 1);
        String expected = checksumLine.substring("sha256=".length());
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("workspace checksum invalid");
        }
        String actual = sha256(body.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("workspace checksum mismatch");
        }

        String[] lines = body.split("\n");
        if (lines.length < 5 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("workspace header invalid");
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        Map<String, String> values = new TreeMap<>();

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("workspace record invalid");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (key.startsWith("e.")) {
                String decodedKey = decodeText(key.substring(2));
                String decodedValue = decodeText(value);
                if (values.put(decodedKey, decodedValue) != null) {
                    throw new IllegalArgumentException("duplicate workspace entry");
                }
            } else {
                if (metadata.put(key, value) != null) {
                    throw new IllegalArgumentException("duplicate workspace metadata");
                }
            }
        }

        if (!metadata.keySet().equals(
                java.util.Set.of("id", "schema", "revision", "size"))) {
            throw new IllegalArgumentException("workspace metadata incomplete");
        }

        String workspaceId = decodeText(metadata.get("id"));
        int schemaVersion = parseInt(metadata.get("schema"), "schema");
        long revision = parseLong(metadata.get("revision"), "revision");
        int size = parseInt(metadata.get("size"), "size");
        if (size != values.size() || size > WorkspaceSnapshot.MAX_ENTRIES) {
            throw new IllegalArgumentException("workspace size mismatch");
        }

        return WorkspaceSnapshot.restore(workspaceId, schemaVersion, revision, values);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        try {
            return new String(
                    Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("workspace base64 invalid", error);
        }
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(field + " invalid", error);
        }
    }

    private static long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(field + " invalid", error);
        }
    }

    private static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(value);
            StringBuilder out = new StringBuilder(result.length * 2);
            for (byte item : result) {
                out.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
