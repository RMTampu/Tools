package com.toolbox.tools.product;

import java.util.List;
import java.util.Objects;

public final class ImportSecurityValidator {
    public static final int MAX_ENTRIES = 4096;
    public static final long MAX_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L;

    public static final class Entry {
        private final String path;
        private final long uncompressedBytes;

        public Entry(String path, long uncompressedBytes) {
            this.path = Objects.requireNonNull(path, "path");
            this.uncompressedBytes = uncompressedBytes;
        }

        public String path() { return path; }
        public long uncompressedBytes() { return uncompressedBytes; }
    }

    public String validate(List<Entry> entries) {
        if (entries == null) return "IMPORT_LIST_MISSING";
        if (entries.size() > MAX_ENTRIES) return "IMPORT_ENTRY_LIMIT";
        long total = 0;
        for (Entry entry : entries) {
            String path = entry.path().replace('\\', '/');
            if (path.startsWith("/")
                    || path.contains("../")
                    || path.contains("/./")
                    || path.contains("//")
                    || path.indexOf('\0') >= 0) {
                return "IMPORT_PATH_TRAVERSAL";
            }
            if (entry.uncompressedBytes() < 0) return "IMPORT_SIZE_INVALID";
            total += entry.uncompressedBytes();
            if (total > MAX_UNCOMPRESSED_BYTES) return "IMPORT_SIZE_BUDGET";
        }
        return "PASS";
    }
}
