package com.toolbox.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class FixtureProjectProvider extends ContentProvider {
    private static final String PROJECT_ID = "project.fixture";
    private static final Map<Long, String> HISTORY = new TreeMap<>();
    private static long currentRevision = 1;

    @Override
    public boolean onCreate() {
        synchronized (HISTORY) {
            if (HISTORY.isEmpty()) {
                HISTORY.put(
                        1L,
                        encodeProject(
                                1,
                                java.util.Collections.singletonMap(
                                        "ui.screen.home.title",
                                        "Fixture Original"
                                )
                        )
                );
            }
        }
        return true;
    }

    @Override
    public Bundle call(
            String method,
            String arg,
            Bundle extras
    ) {
        synchronized (HISTORY) {
            Bundle out = new Bundle();
            try {
                requireProject(extras);
                switch (method) {
                    case "toolbox.describe":
                        out.putInt("protocolVersion", 1);
                        out.putBoolean("writable", true);
                        out.putString("projectId", PROJECT_ID);
                        out.putLong("revision", currentRevision);
                        return out;
                    case "toolbox.load":
                        out.putString(
                                "projectPayload",
                                HISTORY.get(currentRevision)
                        );
                        return out;
                    case "toolbox.commit":
                        return commit(extras);
                    case "toolbox.loadRevision":
                        return loadRevision(extras);
                    case "toolbox.recoverRevision":
                        return recoverRevision(extras);
                    case "toolbox.recoverState":
                        return recoverState(extras);
                    default:
                        return error("METHOD_UNSUPPORTED");
                }
            } catch (Exception error) {
                return error(
                        error.getMessage() == null
                                ? "FIXTURE_PROVIDER_ERROR"
                                : error.getMessage()
                );
            }
        }
    }

    private Bundle commit(Bundle extras) throws Exception {
        long expected = extras.getLong(
                "expectedRevision",
                -1
        );
        if (expected != currentRevision) {
            return error("STALE_REVISION");
        }
        String payload = extras.getString("projectPayload");
        Parsed parsed = parseAndVerify(payload);
        if (!PROJECT_ID.equals(parsed.projectId)
                || parsed.revision != expected) {
            return error("PROJECT_OR_REVISION_MISMATCH");
        }
        long next = expected + 1;
        String committed = withRevision(payload, next);
        HISTORY.put(next, committed);
        currentRevision = next;

        Bundle out = new Bundle();
        out.putString("projectPayload", committed);
        out.putLong("revision", currentRevision);
        return out;
    }

    private Bundle loadRevision(Bundle extras) throws Exception {
        long revision = extras.getLong("revision", -1);
        String payload = HISTORY.get(revision);
        if (payload == null) return error("REVISION_NOT_FOUND");
        Bundle out = new Bundle();
        out.putString("projectPayload", payload);
        return out;
    }

    private Bundle recoverRevision(Bundle extras) throws Exception {
        long revision = extras.getLong("revision", -1);
        String source = HISTORY.get(revision);
        if (source == null) return error("REVISION_NOT_FOUND");
        long next = currentRevision + 1;
        String recovered = withRevision(source, next);
        HISTORY.put(next, recovered);
        currentRevision = next;
        Bundle out = new Bundle();
        out.putString("projectPayload", recovered);
        out.putLong("revision", next);
        return out;
    }

    private Bundle recoverState(Bundle extras) throws Exception {
        String payload = extras.getString("projectPayload");
        Parsed parsed = parseAndVerify(payload);
        if (!PROJECT_ID.equals(parsed.projectId)) {
            return error("PROJECT_ID_MISMATCH");
        }
        long next = currentRevision + 1;
        String recovered = withRevision(payload, next);
        HISTORY.put(next, recovered);
        currentRevision = next;
        Bundle out = new Bundle();
        out.putString("projectPayload", recovered);
        out.putLong("revision", next);
        return out;
    }

    private static void requireProject(Bundle extras) {
        if (extras == null
                || !PROJECT_ID.equals(
                        extras.getString("projectId")
                )) {
            throw new IllegalArgumentException(
                    "PROJECT_ID_MISMATCH"
            );
        }
    }

    private static Bundle error(String message) {
        Bundle out = new Bundle();
        out.putString("error", message);
        return out;
    }

    private static String withRevision(
            String encoded,
            long nextRevision
    ) throws Exception {
        Parsed parsed = parseAndVerify(encoded);
        String body = parsed.body.replace(
                "revision=" + parsed.revision + "\n",
                "revision=" + nextRevision + "\n"
        );
        return body + "sha256="
                + sha256(
                        body.getBytes(StandardCharsets.UTF_8)
                )
                + "\n";
    }

    private static Parsed parseAndVerify(String encoded)
            throws Exception {
        if (encoded == null || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException(
                    "PROJECT_PAYLOAD_INVALID"
            );
        }
        int checksum = encoded.lastIndexOf("sha256=");
        if (checksum <= 0) {
            throw new IllegalArgumentException(
                    "PROJECT_CHECKSUM_MISSING"
            );
        }
        String body = encoded.substring(0, checksum);
        String expected = encoded.substring(
                checksum + "sha256=".length(),
                encoded.length() - 1
        );
        String actual = sha256(
                body.getBytes(StandardCharsets.UTF_8)
        );
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException(
                    "PROJECT_CHECKSUM_MISMATCH"
            );
        }

        String projectId = null;
        long revision = -1;
        for (String line : body.split("\n")) {
            if (line.startsWith("projectId=")) {
                projectId = new String(
                        Base64.getUrlDecoder().decode(
                                line.substring("projectId=".length())
                        ),
                        StandardCharsets.UTF_8
                );
            } else if (line.startsWith("revision=")) {
                revision = Long.parseLong(
                        line.substring("revision=".length())
                );
            }
        }
        if (projectId == null || revision < 0) {
            throw new IllegalArgumentException(
                    "PROJECT_META_MISSING"
            );
        }
        return new Parsed(
                projectId,
                revision,
                body
        );
    }

    private static String encodeProject(
            long revision,
            Map<String, String> resources
    ) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("TBX_PROJECT_V1\n");
            body.append("projectId=")
                    .append(b64(PROJECT_ID))
                    .append('\n');
            body.append("schemaVersion=1\n");
            body.append("buildModelVersion=1\n");
            body.append("revision=")
                    .append(revision)
                    .append('\n');
            body.append("lifecycle=ACTIVE\n");
            body.append("resourceCount=")
                    .append(resources.size())
                    .append('\n');
            body.append("referenceCount=0\n");
            body.append("dependencyCount=0\n");
            for (Map.Entry<String, String> entry
                    : new TreeMap<>(resources).entrySet()) {
                body.append("resource.")
                        .append(b64(entry.getKey()))
                        .append('=')
                        .append(b64(entry.getValue()))
                        .append('\n');
            }
            byte[] raw = body.toString()
                    .getBytes(StandardCharsets.UTF_8);
            return body.append("sha256=")
                    .append(sha256(raw))
                    .append('\n')
                    .toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)
                );
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");
        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest(bytes)) {
            out.append(String.format(
                    java.util.Locale.ROOT,
                    "%02x",
                    value
            ));
        }
        return out.toString();
    }

    private static final class Parsed {
        final String projectId;
        final long revision;
        final String body;

        Parsed(
                String projectId,
                long revision,
                String body
        ) {
            this.projectId = projectId;
            this.revision = revision;
            this.body = body;
        }
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.toolbox.project+json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(
            Uri uri,
            String selection,
            String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs
    ) {
        return 0;
    }
}
