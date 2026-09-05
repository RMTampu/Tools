package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.toolbox.tools.core.VisibleWorkspaceStore;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

public final class ExternalAssetGateway {
    public static final long MAX_BYTES = 128L * 1024L * 1024L;

    public static final class Descriptor {
        private final Uri sourceUri;
        private final String displayName;
        private final String mime;
        private final long sizeBytes;
        private final String fingerprint;
        private final String storageName;

        Descriptor(
                Uri sourceUri,
                String displayName,
                String mime,
                long sizeBytes,
                String fingerprint,
                String storageName
        ) {
            this.sourceUri = sourceUri;
            this.displayName = displayName;
            this.mime = mime;
            this.sizeBytes = sizeBytes;
            this.fingerprint = fingerprint;
            this.storageName = storageName;
        }

        public Uri uri() { return sourceUri; }
        public String displayName() { return displayName; }
        public String mime() { return mime; }
        public long sizeBytes() { return sizeBytes; }
        public String fingerprint() { return fingerprint; }
        public String storageName() { return storageName; }
        public String assetId() {
            return "asset.external." + fingerprint.substring(0, 16);
        }
    }

    public Descriptor importToWorkspace(
            ContentResolver resolver,
            Uri uri,
            int flags,
            VisibleWorkspaceStore visible
    ) throws Exception {
        Objects.requireNonNull(visible, "visible");
        Inspection inspection = inspect(resolver, uri, flags);
        String storageName = storageName(
                inspection.displayName,
                inspection.mime,
                inspection.fingerprint
        );

        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException("ASSET_STREAM_UNAVAILABLE");
            }
            VisibleWorkspaceStore.WriteResult stored =
                    visible.writeStream(
                            VisibleWorkspaceStore.Area.ASSETS,
                            storageName,
                            input,
                            MAX_BYTES
                    );
            if (stored.bytesWritten() != inspection.sizeBytes
                    || !stored.sha256().equals(inspection.fingerprint)) {
                throw new IllegalArgumentException(
                        "ASSET_VISIBLE_COPY_VERIFICATION_FAILED"
                );
            }
        }

        return new Descriptor(
                uri,
                inspection.displayName,
                inspection.mime,
                inspection.sizeBytes,
                inspection.fingerprint,
                storageName
        );
    }

    public Descriptor inspectAndPersist(
            ContentResolver resolver,
            Uri uri,
            int flags
    ) throws Exception {
        Inspection inspection = inspect(resolver, uri, flags);
        return new Descriptor(
                uri,
                inspection.displayName,
                inspection.mime,
                inspection.sizeBytes,
                inspection.fingerprint,
                null
        );
    }

    private Inspection inspect(
            ContentResolver resolver,
            Uri uri,
            int flags
    ) throws Exception {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(uri, "uri");
        if (!"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException("ASSET_URI_MUST_BE_CONTENT");
        }

        int grant = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            resolver.takePersistableUriPermission(uri, grant);
        } catch (SecurityException ignored) {
            // Original is copied into user-owned ToolBox/Assets, so a
            // non-persistable source URI does not become source-of-truth.
        }

        String mime = resolver.getType(uri);
        if (mime == null) mime = "application/octet-stream";
        if (!allowedMime(mime)) {
            throw new IllegalArgumentException(
                    "ASSET_MIME_UNSUPPORTED:" + mime
            );
        }

        String name = "asset";
        long declaredSize = -1;
        try (Cursor cursor = resolver.query(
                uri,
                new String[] {
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE
                },
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                if (!cursor.isNull(0)) name = cursor.getString(0);
                if (!cursor.isNull(1)) declaredSize = cursor.getLong(1);
            }
        }
        if (declaredSize > MAX_BYTES) {
            throw new IllegalArgumentException("ASSET_SIZE_LIMIT");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException(
                        "ASSET_STREAM_UNAVAILABLE"
                );
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IllegalArgumentException(
                            "ASSET_SIZE_LIMIT"
                    );
                }
                digest.update(buffer, 0, read);
            }
        }
        if (declaredSize >= 0 && declaredSize != total) {
            throw new IllegalArgumentException(
                    "ASSET_DECLARED_SIZE_MISMATCH"
            );
        }

        return new Inspection(
                sanitizeDisplayName(name),
                mime,
                total,
                hex(digest.digest())
        );
    }

    public static boolean allowedMime(String mime) {
        if (mime == null) return false;
        return mime.startsWith("image/")
                || mime.startsWith("audio/")
                || mime.startsWith("video/")
                || mime.startsWith("font/")
                || "application/json".equals(mime)
                || "application/octet-stream".equals(mime)
                || "application/font-sfnt".equals(mime)
                || "application/vnd.ms-fontobject".equals(mime);
    }

    public static String kindForMime(String mime) {
        if (mime.startsWith("image/")) return "IMAGE";
        if (mime.startsWith("audio/")) return "AUDIO";
        if (mime.startsWith("video/")) return "VIDEO";
        if (mime.startsWith("font/") || mime.contains("font")) {
            return "FONT";
        }
        if ("application/json".equals(mime)) return "JSON";
        return "RAW";
    }

    private static String storageName(
            String displayName,
            String mime,
            String fingerprint
    ) {
        String extension = extension(displayName, mime);
        return "asset-"
                + fingerprint.substring(0, 24)
                + extension;
    }

    private static String extension(String name, String mime) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String raw = name.substring(dot)
                    .toLowerCase(Locale.ROOT);
            if (raw.matches("\\.[a-z0-9]{1,8}")) return raw;
        }
        if ("image/png".equals(mime)) return ".png";
        if ("image/jpeg".equals(mime)) return ".jpg";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/svg+xml".equals(mime)) return ".svg";
        if (mime.startsWith("audio/")) return ".audio";
        if (mime.startsWith("video/")) return ".video";
        if (mime.startsWith("font/") || mime.contains("font")) {
            return ".font";
        }
        if ("application/json".equals(mime)) return ".json";
        return ".bin";
    }

    private static String sanitizeDisplayName(String name) {
        if (name == null || name.trim().isEmpty()) return "asset";
        String value = name.trim().replaceAll("[\\r\\n\\t]", "_");
        if (value.length() > 160) value = value.substring(0, 160);
        return value;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value));
        }
        return out.toString();
    }

    private static final class Inspection {
        final String displayName;
        final String mime;
        final long sizeBytes;
        final String fingerprint;

        Inspection(
                String displayName,
                String mime,
                long sizeBytes,
                String fingerprint
        ) {
            this.displayName = displayName;
            this.mime = mime;
            this.sizeBytes = sizeBytes;
            this.fingerprint = fingerprint;
        }
    }
}
