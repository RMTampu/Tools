package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.StableId;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ImportSecurityValidator {
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_NESTING_DEPTH = 16;
    public static final int MAX_PATH_LENGTH = 240;
    public static final long MAX_SINGLE_ENTRY_BYTES =
            128L * 1024L * 1024L;
    public static final long MAX_COMPRESSED_BYTES =
            96L * 1024L * 1024L;
    public static final long MAX_UNCOMPRESSED_BYTES =
            256L * 1024L * 1024L;
    public static final double MAX_DECOMPRESSION_RATIO = 100.0d;

    private static final Set<String> BLOCKED_SUFFIXES;
    static {
        LinkedHashSet<String> blocked = new LinkedHashSet<>();
        Collections.addAll(
                blocked,
                ".apk",
                ".aab",
                ".dex",
                ".odex",
                ".vdex",
                ".so",
                ".jar",
                ".class",
                ".exe",
                ".dll",
                ".elf",
                ".sh",
                ".bat",
                ".cmd",
                ".ps1"
        );
        BLOCKED_SUFFIXES = Collections.unmodifiableSet(blocked);
    }

    public static final class Entry {
        private final String path;
        private final long compressedBytes;
        private final long uncompressedBytes;
        private final int nestingDepth;
        private final String contentType;
        private final String expectedSha256;
        private final String actualSha256;
        private final String stableId;

        /**
         * Compatibility constructor for a plain, already-expanded file.
         */
        public Entry(String path, long uncompressedBytes) {
            this(
                    path,
                    Math.max(0L, uncompressedBytes),
                    uncompressedBytes,
                    deriveDepth(path),
                    "application/octet-stream",
                    null,
                    null,
                    null
            );
        }

        public Entry(
                String path,
                long compressedBytes,
                long uncompressedBytes,
                int nestingDepth,
                String contentType,
                String expectedSha256,
                String actualSha256,
                String stableId
        ) {
            this.path = Objects.requireNonNull(path, "path");
            this.compressedBytes = compressedBytes;
            this.uncompressedBytes = uncompressedBytes;
            this.nestingDepth = nestingDepth;
            this.contentType = contentType == null
                    ? "application/octet-stream"
                    : contentType.trim().toLowerCase(Locale.ROOT);
            this.expectedSha256 = expectedSha256;
            this.actualSha256 = actualSha256;
            this.stableId = stableId;
        }

        public String path() { return path; }
        public long compressedBytes() { return compressedBytes; }
        public long uncompressedBytes() { return uncompressedBytes; }
        public int nestingDepth() { return nestingDepth; }
        public String contentType() { return contentType; }
        public String expectedSha256() { return expectedSha256; }
        public String actualSha256() { return actualSha256; }
        public String stableId() { return stableId; }
    }

    public static final class Request {
        private final List<Entry> entries;
        private final int schemaVersion;
        private final int buildModelVersion;
        private final boolean signatureRequired;
        private final boolean signatureVerified;
        private final String expectedPackageSha256;
        private final String actualPackageSha256;

        public Request(
                List<Entry> entries,
                int schemaVersion,
                int buildModelVersion,
                boolean signatureRequired,
                boolean signatureVerified,
                String expectedPackageSha256,
                String actualPackageSha256
        ) {
            this.entries = entries;
            this.schemaVersion = schemaVersion;
            this.buildModelVersion = buildModelVersion;
            this.signatureRequired = signatureRequired;
            this.signatureVerified = signatureVerified;
            this.expectedPackageSha256 = expectedPackageSha256;
            this.actualPackageSha256 = actualPackageSha256;
        }

        public List<Entry> entries() { return entries; }
        public int schemaVersion() { return schemaVersion; }
        public int buildModelVersion() { return buildModelVersion; }
        public boolean signatureRequired() { return signatureRequired; }
        public boolean signatureVerified() { return signatureVerified; }
        public String expectedPackageSha256() {
            return expectedPackageSha256;
        }
        public String actualPackageSha256() {
            return actualPackageSha256;
        }
    }

    public String validate(List<Entry> entries) {
        return validate(new Request(
                entries,
                ProjectState.CURRENT_SCHEMA_VERSION,
                ProjectState.CURRENT_BUILD_MODEL_VERSION,
                false,
                false,
                null,
                null
        ));
    }

    public String validate(Request request) {
        if (request == null) return "IMPORT_REQUEST_MISSING";
        List<Entry> entries = request.entries();
        if (entries == null) return "IMPORT_LIST_MISSING";
        if (entries.isEmpty()) return "IMPORT_EMPTY";
        if (entries.size() > MAX_ENTRIES) {
            return "IMPORT_ENTRY_LIMIT";
        }

        if (request.schemaVersion()
                != ProjectState.CURRENT_SCHEMA_VERSION) {
            return "IMPORT_SCHEMA_INCOMPATIBLE";
        }
        if (request.buildModelVersion()
                != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            return "IMPORT_BUILD_MODEL_INCOMPATIBLE";
        }
        if (request.signatureRequired()
                && !request.signatureVerified()) {
            return "IMPORT_SIGNATURE_REQUIRED";
        }

        String packageHashError = validateOptionalHashPair(
                request.expectedPackageSha256(),
                request.actualPackageSha256(),
                "IMPORT_PACKAGE_HASH"
        );
        if (packageHashError != null) return packageHashError;

        long totalCompressed = 0L;
        long totalUncompressed = 0L;
        Set<String> paths = new HashSet<>();
        Set<String> foldedPaths = new HashSet<>();

        for (Entry entry : entries) {
            if (entry == null) return "IMPORT_ENTRY_NULL";

            String pathError = validatePath(entry.path());
            if (pathError != null) return pathError;
            String canonicalPath = canonical(entry.path());
            if (!paths.add(canonicalPath)) {
                return "IMPORT_DUPLICATE_PATH";
            }
            if (!foldedPaths.add(
                    canonicalPath.toLowerCase(Locale.ROOT)
            )) {
                return "IMPORT_CASE_COLLISION";
            }

            if (entry.nestingDepth() < 0
                    || entry.nestingDepth()
                        > MAX_NESTING_DEPTH
                    || entry.nestingDepth()
                        != deriveDepth(canonicalPath)) {
                return "IMPORT_NESTING_LIMIT";
            }

            if (entry.compressedBytes() < 0
                    || entry.uncompressedBytes() < 0) {
                return "IMPORT_SIZE_INVALID";
            }
            if (entry.uncompressedBytes()
                    > MAX_SINGLE_ENTRY_BYTES) {
                return "IMPORT_SINGLE_ENTRY_BUDGET";
            }

            totalCompressed = safeAdd(
                    totalCompressed,
                    entry.compressedBytes()
            );
            totalUncompressed = safeAdd(
                    totalUncompressed,
                    entry.uncompressedBytes()
            );
            if (totalCompressed < 0
                    || totalCompressed > MAX_COMPRESSED_BYTES) {
                return "IMPORT_COMPRESSED_BUDGET";
            }
            if (totalUncompressed < 0
                    || totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                return "IMPORT_SIZE_BUDGET";
            }

            if (entry.uncompressedBytes() > 0) {
                if (entry.compressedBytes() <= 0) {
                    return "IMPORT_DECOMPRESSION_RATIO";
                }
                double ratio =
                        (double) entry.uncompressedBytes()
                                / (double) entry.compressedBytes();
                if (!Double.isFinite(ratio)
                        || ratio > MAX_DECOMPRESSION_RATIO) {
                    return "IMPORT_DECOMPRESSION_RATIO";
                }
            }

            if (!allowedContentType(
                    entry.contentType(),
                    canonicalPath
            )) {
                return "IMPORT_CONTENT_TYPE";
            }
            if (blockedExecutable(canonicalPath)) {
                return "IMPORT_EXECUTABLE_BLOCKED";
            }

            String hashError = validateOptionalHashPair(
                    entry.expectedSha256(),
                    entry.actualSha256(),
                    "IMPORT_ENTRY_HASH"
            );
            if (hashError != null) return hashError;

            if (entry.stableId() != null) {
                try {
                    StableId.require(
                            entry.stableId(),
                            "importStableId"
                    );
                } catch (RuntimeException error) {
                    return "IMPORT_STABLE_ID_INVALID";
                }
            }
        }

        return "PASS";
    }

    private static String validatePath(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "IMPORT_PATH_EMPTY";
        }
        if (raw.length() > MAX_PATH_LENGTH) {
            return "IMPORT_PATH_LENGTH";
        }
        if (raw.indexOf('\0') >= 0
                || raw.indexOf('\\') >= 0
                || raw.startsWith("/")
                || raw.startsWith("~")
                || raw.matches("^[A-Za-z]:.*")) {
            return "IMPORT_PATH_TRAVERSAL";
        }

        String normalized = canonical(raw);
        if (normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.contains("//")) {
            return "IMPORT_PATH_TRAVERSAL";
        }
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isEmpty()
                    || ".".equals(segment)
                    || "..".equals(segment)
                    || segment.length() > 120) {
                return "IMPORT_PATH_TRAVERSAL";
            }
        }
        return null;
    }

    private static String canonical(String path) {
        return path.replace('\\', '/');
    }

    private static int deriveDepth(String path) {
        if (path == null || path.isEmpty()) return 0;
        String normalized = canonical(path);
        int depth = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '/') depth++;
        }
        return depth;
    }

    private static boolean allowedContentType(
            String type,
            String path
    ) {
        if (type == null || type.trim().isEmpty()) return false;
        String mime = type.trim().toLowerCase(Locale.ROOT);
        if ("application/vnd.toolbox.project+json".equals(mime)
                || "application/vnd.toolbox.manifest+text".equals(mime)
                || "application/json".equals(mime)
                || "text/plain".equals(mime)
                || "application/octet-stream".equals(mime)) {
            return true;
        }
        if (mime.startsWith("image/")
                || mime.startsWith("audio/")
                || mime.startsWith("video/")
                || mime.startsWith("font/")
                || mime.contains("font")) {
            return true;
        }
        // Unknown content is never accepted merely because the suffix looks safe.
        return false;
    }

    private static boolean blockedExecutable(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String suffix : BLOCKED_SUFFIXES) {
            if (lower.endsWith(suffix)) return true;
        }
        return false;
    }

    private static String validateOptionalHashPair(
            String expected,
            String actual,
            String prefix
    ) {
        if (expected == null && actual == null) return null;
        if (expected == null || actual == null) {
            return prefix + "_INCOMPLETE";
        }
        String left = expected.toLowerCase(Locale.ROOT);
        String right = actual.toLowerCase(Locale.ROOT);
        if (!left.matches("[0-9a-f]{64}")
                || !right.matches("[0-9a-f]{64}")) {
            return prefix + "_INVALID";
        }
        if (!java.security.MessageDigest.isEqual(
                left.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                right.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            return prefix + "_MISMATCH";
        }
        return null;
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return -1L;
        }
        return left + right;
    }
}
