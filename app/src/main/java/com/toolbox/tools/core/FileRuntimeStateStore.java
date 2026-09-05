package com.toolbox.tools.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public final class FileRuntimeStateStore implements RuntimeStateStore {
    private static final String META_SCHEMA =
            "_toolbox.runtime.schema";
    private static final String META_SHA256 =
            "_toolbox.runtime.sha256";
    private static final String SCHEMA = "1";

    private final File file;
    private final Map<String, String> values = new LinkedHashMap<>();

    public FileRuntimeStateStore(File file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
        load();
    }

    @Override
    public synchronized String get(String key) {
        return values.get(StableId.require(key, "runtimeStateKey"));
    }

    @Override
    public synchronized void put(String key, String value) {
        values.put(
                StableId.require(key, "runtimeStateKey"),
                java.util.Objects.requireNonNull(value, "value")
        );
        persist();
    }

    @Override
    public synchronized void remove(String key) {
        values.remove(StableId.require(key, "runtimeStateKey"));
        persist();
    }

    @Override
    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(values)
        );
    }

    private synchronized void load() {
        values.clear();

        Candidate primary = readCandidate(file);
        if (primary.valid) {
            values.putAll(primary.values);
            cleanupRecoveryFiles();
            if (primary.legacy) {
                // Upgrade metadata lama ke format checksummed tanpa mengubah
                // state semantik.
                persist();
            }
            return;
        }

        File pending = pendingFile();
        Candidate pendingCandidate = readCandidate(pending);
        if (pendingCandidate.valid) {
            values.putAll(pendingCandidate.values);
            persist();
            return;
        }

        File backup = backupFile();
        Candidate backupCandidate = readCandidate(backup);
        if (backupCandidate.valid) {
            values.putAll(backupCandidate.values);
            persist();
            return;
        }

        if (file.exists() || pending.exists() || backup.exists()) {
            // Metadata runtime yang rusak tidak boleh membuat aplikasi diam-
            // diam masuk project lain. Fail closed ke Safe UI.
            values.put("recovery.required", "true");
            values.put("recovery.reason", "RUNTIME_STATE_CORRUPT");
            values.put("recovery.operation", "BOOTSTRAP");
            values.put("safe.mode", "true");
            persist();
        }
    }

    private Candidate readCandidate(File source) {
        if (source == null || !source.isFile()) {
            return Candidate.missing();
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(source)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException error) {
            return Candidate.invalid();
        }

        LinkedHashMap<String, String> loaded =
                new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (META_SCHEMA.equals(key) || META_SHA256.equals(key)) {
                continue;
            }
            String value = properties.getProperty(key);
            if (value == null) return Candidate.invalid();
            try {
                StableId.require(key, "runtimeStateKey");
            } catch (RuntimeException error) {
                return Candidate.invalid();
            }
            loaded.put(key, value);
        }

        String schema = properties.getProperty(META_SCHEMA);
        String sha = properties.getProperty(META_SHA256);
        if (schema == null && sha == null) {
            return Candidate.legacy(loaded);
        }
        if (!SCHEMA.equals(schema)
                || sha == null
                || !sha.matches("[0-9a-f]{64}")) {
            return Candidate.invalid();
        }
        String actual = checksum(loaded);
        if (!constantEquals(sha, actual)) {
            return Candidate.invalid();
        }
        return Candidate.valid(loaded);
    }

    private synchronized void persist() {
        File parent = file.getParentFile();
        if (parent != null
                && !parent.isDirectory()
                && !parent.mkdirs()) {
            throw new IllegalStateException(
                    "secure runtime metadata directory unavailable"
            );
        }

        File pending = pendingFile();
        File backup = backupFile();

        Properties properties = new Properties();
        properties.putAll(values);
        properties.setProperty(META_SCHEMA, SCHEMA);
        properties.setProperty(META_SHA256, checksum(values));

        try (FileOutputStream output = new FileOutputStream(pending)) {
            properties.store(
                    output,
                    "ToolBox secure runtime metadata"
            );
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            pending.delete();
            throw new IllegalStateException(
                    "secure runtime metadata write failed",
                    error
            );
        }

        Candidate pendingCandidate = readCandidate(pending);
        if (!pendingCandidate.valid
                || !pendingCandidate.values.equals(values)) {
            pending.delete();
            throw new IllegalStateException(
                    "secure runtime metadata verification failed"
            );
        }

        if (backup.exists() && !backup.delete()) {
            pending.delete();
            throw new IllegalStateException(
                    "secure runtime metadata stale backup unavailable"
            );
        }

        boolean hadPrimary = file.exists();
        if (hadPrimary && !file.renameTo(backup)) {
            pending.delete();
            throw new IllegalStateException(
                    "secure runtime metadata backup swap failed"
            );
        }

        if (!pending.renameTo(file)) {
            if (hadPrimary) {
                backup.renameTo(file);
            }
            throw new IllegalStateException(
                    "secure runtime metadata publish failed"
            );
        }

        Candidate published = readCandidate(file);
        if (!published.valid
                || !published.values.equals(values)) {
            file.delete();
            if (hadPrimary) {
                backup.renameTo(file);
            }
            throw new IllegalStateException(
                    "secure runtime metadata published state invalid"
            );
        }

        if (backup.exists() && !backup.delete()) {
            // State utama sudah terverifikasi; backup lama aman dibiarkan
            // untuk dibersihkan saat startup berikutnya.
        }
    }

    private void cleanupRecoveryFiles() {
        File pending = pendingFile();
        if (pending.exists()) pending.delete();
        File backup = backupFile();
        if (backup.exists()) backup.delete();
    }

    private File pendingFile() {
        return new File(file.getPath() + ".pending");
    }

    private File backupFile() {
        return new File(file.getPath() + ".backup");
    }

    private static String checksum(Map<String, String> input) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry
                : new TreeMap<>(input).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            canonical.append(key.length())
                    .append(':')
                    .append(key)
                    .append(':')
                    .append(value.length())
                    .append(':')
                    .append(value)
                    .append('\n');
        }
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    canonical.toString()
                            .getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder out = new StringBuilder();
            for (byte value : bytes) {
                out.append(String.format(
                        Locale.ROOT,
                        "%02x",
                        value
                ));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    error
            );
        }
    }

    private static boolean constantEquals(
            String expected,
            String actual
    ) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static final class Candidate {
        final boolean valid;
        final boolean legacy;
        final Map<String, String> values;

        Candidate(
                boolean valid,
                boolean legacy,
                Map<String, String> values
        ) {
            this.valid = valid;
            this.legacy = legacy;
            this.values = values;
        }

        static Candidate missing() {
            return new Candidate(
                    false,
                    false,
                    Collections.emptyMap()
            );
        }

        static Candidate invalid() {
            return missing();
        }

        static Candidate legacy(Map<String, String> values) {
            return new Candidate(
                    true,
                    true,
                    new LinkedHashMap<>(values)
            );
        }

        static Candidate valid(Map<String, String> values) {
            return new Candidate(
                    true,
                    false,
                    new LinkedHashMap<>(values)
            );
        }
    }
}
