package io.toolbox.stagea.android;

import io.toolbox.stagea.StageAContracts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class StateFileCodec {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_KEY_LENGTH = 128;
    public static final int MAX_VALUE_LENGTH = 4096;
    public static final int MAX_PAYLOAD_BYTES = 131072;
    private static final int MAGIC = 0x54425841;
    private static final int CHECKSUM_BYTES = 32;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private StateFileCodec() {}

    public static byte[] encode(Map<String, String> input) {
        if (input == null) throw invalid("state map is null");
        if (input.size() > MAX_ENTRIES) throw limit("too many state entries");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> row : input.entrySet()) {
            String key = requireKey(row.getKey());
            String value = requireValue(row.getValue());
            if (sorted.put(key, value) != null) throw invalid("duplicate state key");
        }
        try {
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bodyBytes);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(sorted.size());
            for (Map.Entry<String, String> row : sorted.entrySet()) {
                writeUtf8(out, row.getKey());
                writeUtf8(out, row.getValue());
            }
            out.flush();
            byte[] body = bodyBytes.toByteArray();
            if (body.length + CHECKSUM_BYTES > MAX_PAYLOAD_BYTES) throw limit("state payload too large");
            byte[] checksum = digest(body);
            ByteArrayOutputStream payload = new ByteArrayOutputStream(body.length + checksum.length);
            payload.write(body);
            payload.write(checksum);
            return payload.toByteArray();
        } catch (IOException impossible) {
            throw new StageAContracts.StageAException("state.codec.failure", "Unable to encode state", impossible);
        }
    }

    public static Map<String, String> decode(byte[] payload) {
        if (payload == null) throw invalid("state payload is null");
        if (payload.length < 12 + CHECKSUM_BYTES || payload.length > MAX_PAYLOAD_BYTES) {
            throw invalid("state payload size is invalid");
        }
        int bodyLength = payload.length - CHECKSUM_BYTES;
        byte[] body = java.util.Arrays.copyOfRange(payload, 0, bodyLength);
        byte[] expected = java.util.Arrays.copyOfRange(payload, bodyLength, payload.length);
        if (!MessageDigest.isEqual(expected, digest(body))) {
            throw new StageAContracts.StageAException("state.checksum.invalid", "State checksum is invalid");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
            if (in.readInt() != MAGIC) throw invalid("state magic is invalid");
            if (in.readInt() != FORMAT_VERSION) {
                throw new StageAContracts.StageAException("state.version.unsupported", "State format version is unsupported");
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw invalid("state entry count is invalid");
            LinkedHashMap<String, String> decoded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String key = requireKey(readUtf8(in, MAX_KEY_LENGTH));
                String value = requireValue(readUtf8(in, MAX_VALUE_LENGTH));
                if (decoded.put(key, value) != null) throw invalid("duplicate state key");
            }
            if (in.available() != 0) throw invalid("trailing state bytes");
            return Collections.unmodifiableMap(decoded);
        } catch (IOException failure) {
            throw new StageAContracts.StageAException("state.decode.failure", "Unable to decode state", failure);
        }
    }

    public static String requireKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw invalid("state key is invalid");
        return key;
    }

    public static String requireValue(String value) {
        if (value == null || value.length() > MAX_VALUE_LENGTH) throw limit("state value is invalid or too large");
        return value;
    }

    private static void writeUtf8(DataOutputStream out, String value) throws IOException {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(raw.length);
        out.write(raw);
    }

    private static String readUtf8(DataInputStream in, int maxChars) throws IOException {
        int byteLength = in.readInt();
        if (byteLength < 0 || byteLength > MAX_PAYLOAD_BYTES) throw invalid("state string length is invalid");
        byte[] raw = new byte[byteLength];
        in.readFully(raw);
        String value = new String(raw, StandardCharsets.UTF_8);
        if (value.length() > maxChars) throw limit("state string exceeds contract");
        return value;
    }

    private static byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new StageAContracts.StageAException("state.digest.unavailable", "SHA-256 unavailable", impossible);
        }
    }

    private static StageAContracts.StageAException invalid(String message) {
        return new StageAContracts.StageAException("state.format.invalid", message);
    }

    private static StageAContracts.StageAException limit(String message) {
        return new StageAContracts.StageAException("state.resource.limit", message);
    }
}
