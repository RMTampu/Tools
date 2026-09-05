package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

public final class ExternalAssetGateway {
    public static final long MAX_BYTES = 128L * 1024L * 1024L;

    public static final class Descriptor {
        private final Uri uri;
        private final String displayName;
        private final String mime;
        private final long sizeBytes;
        private final String fingerprint;

        Descriptor(Uri uri, String displayName, String mime, long sizeBytes, String fingerprint) {
            this.uri = uri;
            this.displayName = displayName;
            this.mime = mime;
            this.sizeBytes = sizeBytes;
            this.fingerprint = fingerprint;
        }

        public Uri uri() { return uri; }
        public String displayName() { return displayName; }
        public String mime() { return mime; }
        public long sizeBytes() { return sizeBytes; }
        public String fingerprint() { return fingerprint; }
        public String assetId() { return "asset.external." + fingerprint.substring(0, 16); }
    }

    public Descriptor inspectAndPersist(ContentResolver resolver, Uri uri, int flags) throws Exception {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(uri, "uri");
        if (!"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException("ASSET_URI_MUST_BE_CONTENT");
        }

        int grant = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            resolver.takePersistableUriPermission(uri, grant);
        } catch (SecurityException ignored) {
            // Beberapa provider memberi grant sesi saja; konten tetap divalidasi sebelum dipakai.
        }

        String mime = resolver.getType(uri);
        if (mime == null) mime = "application/octet-stream";
        if (!allowedMime(mime)) {
            throw new IllegalArgumentException("ASSET_MIME_UNSUPPORTED:" + mime);
        }

        String name = "asset";
        long declaredSize = -1;
        try (Cursor cursor = resolver.query(
                uri,
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
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
            if (input == null) throw new IllegalArgumentException("ASSET_STREAM_UNAVAILABLE");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw new IllegalArgumentException("ASSET_SIZE_LIMIT");
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return new Descriptor(uri, name, mime, total, hex.toString());
    }

    public static boolean allowedMime(String mime) {
        if (mime == null) return false;
        return mime.startsWith("image/")
                || mime.startsWith("audio/")
                || mime.startsWith("video/")
                || mime.startsWith("font/")
                || "application/json".equals(mime)
                || "application/octet-stream".equals(mime)
                || "application/font-sfnt".equals(mime);
    }

    public static String kindForMime(String mime) {
        if (mime.startsWith("image/")) return "IMAGE";
        if (mime.startsWith("audio/")) return "AUDIO";
        if (mime.startsWith("video/")) return "VIDEO";
        if (mime.startsWith("font/") || mime.contains("font")) return "FONT";
        if ("application/json".equals(mime)) return "JSON";
        return "RAW";
    }
}
