package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectLoadResult;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectStore;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.RecoveryCandidate;
import com.toolbox.tools.core.StableId;
import com.toolbox.tools.core.StaleWriteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * ProjectStore backed by a user-selected Storage Access Framework document tree.
 *
 * User project revisions are the source of truth. App-private storage remains
 * reserved for runtime journals/recovery metadata owned by AppKernel.
 */
public final class SafProjectStore implements ProjectStore {
    private static final String MIME = "application/octet-stream";
    private static final int MAX_REVISIONS = 32;

    private final ContentResolver resolver;
    private final Uri treeUri;
    private final Uri parentDocumentUri;
    private final ProjectCodec codec = new ProjectCodec();
    private final ProjectValidator validator = new ProjectValidator();

    public SafProjectStore(ContentResolver resolver, Uri treeUri) {
        this(
                resolver,
                treeUri,
                rootDocumentUri(
                        Objects.requireNonNull(treeUri, "treeUri")
                )
        );
    }

    public SafProjectStore(
            ContentResolver resolver,
            Uri treeUri,
            Uri parentDocumentUri
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.treeUri = Objects.requireNonNull(treeUri, "treeUri");
        this.parentDocumentUri = Objects.requireNonNull(
                parentDocumentUri,
                "parentDocumentUri"
        );
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw new IllegalArgumentException("SAF tree URI required");
        }
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        String parentId = DocumentsContract.getDocumentId(parentDocumentUri);
        if (treeId == null || parentId == null) {
            throw new IllegalArgumentException(
                    "SAF project directory identity required"
            );
        }
    }

    @Override
    public synchronized ProjectLoadResult load(String projectId)
            throws IOException {
        StableId.require(projectId, "projectId");
        Long current = readRefRecoverable("current.ref");
        if (current == null) {
            return new ProjectLoadResult(
                    ProjectAccessStatus.FOLDER_MISSING,
                    null,
                    recoveryCandidates()
            );
        }
        try {
            ProjectState state = loadRevision(current);
            if (!projectId.equals(state.projectId())) {
                return new ProjectLoadResult(
                        ProjectAccessStatus.PROJECT_CORRUPT,
                        null,
                        recoveryCandidates()
                );
            }
            if (state.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION) {
                return new ProjectLoadResult(
                        ProjectAccessStatus.SCHEMA_INCOMPATIBLE,
                        state,
                        recoveryCandidates()
                );
            }
            return new ProjectLoadResult(
                    ProjectAccessStatus.PROJECT_OK,
                    state,
                    recoveryCandidates()
            );
        } catch (IOException error) {
            ProjectState fallback = null;
            Long previous = readRefRecoverable("previous.ref");
            if (previous != null) {
                try {
                    fallback = loadRevision(previous);
                } catch (IOException ignored) {
                    fallback = null;
                }
            }
            return new ProjectLoadResult(
                    ProjectAccessStatus.PROJECT_CORRUPT,
                    fallback,
                    recoveryCandidates()
            );
        }
    }

    @Override
    public synchronized ProjectState commit(
            ProjectState workingState,
            long expectedRevision
    ) throws IOException {
        ProjectValidationResult validation = validator.validate(workingState);
        if (!validation.isPass()) {
            throw new IOException(
                    "PROJECT_VALIDATION_FAILED:" + validation.message()
            );
        }

        Long current = readRefRecoverable("current.ref");
        long actual = current == null ? 0 : current;
        if (actual != expectedRevision) {
            throw new StaleWriteException(expectedRevision, actual);
        }

        long nextRevision = actual + 1;
        ProjectState committed = workingState.withRevision(nextRevision);
        publishRevision(committed);

        if (actual > 0) {
            replaceRef("previous.ref", actual);
        }
        replaceRef("current.ref", nextRevision);
        trimOldRevisions();
        return committed;
    }

    @Override
    public synchronized ProjectState recoverRevision(long revision)
            throws IOException {
        return recoverState(loadRevision(revision));
    }

    @Override
    public synchronized ProjectState recoverState(ProjectState candidate)
            throws IOException {
        ProjectValidationResult validation = validator.validate(candidate);
        if (!validation.isPass()) {
            throw new IOException(
                    "RECOVERY_CANDIDATE_INVALID:" + validation.message()
            );
        }
        Long current = readRefRecoverable("current.ref");
        long actual = current == null ? 0 : current;
        return commit(candidate, actual);
    }

    @Override
    public synchronized ProjectState loadRevision(long revision)
            throws IOException {
        if (revision <= 0) throw new IOException("revision invalid");
        Uri uri = findChild(revisionName(revision));
        if (uri == null) {
            throw new IOException("revision missing:" + revision);
        }
        String encoded = readText(uri);
        try {
            ProjectState state = codec.decode(encoded);
            if (state.revision() != revision) {
                throw new IOException("revision identity mismatch");
            }
            ProjectValidationResult validation = validator.validate(state);
            if (!validation.isPass()
                    && state.schemaVersion()
                    == ProjectState.CURRENT_SCHEMA_VERSION) {
                throw new IOException(
                        "stored project invalid:" + validation.message()
                );
            }
            return state;
        } catch (IllegalArgumentException error) {
            throw new IOException("revision corrupt", error);
        }
    }

    @Override
    public synchronized List<RecoveryCandidate> recoveryCandidates()
            throws IOException {
        long current = valueOr(readRefRecoverable("current.ref"), -1);
        long previous = valueOr(readRefRecoverable("previous.ref"), -1);
        List<Long> revisions = listRevisionIds();
        revisions.sort(Comparator.reverseOrder());

        List<RecoveryCandidate> out = new ArrayList<>();
        for (long revision : revisions) {
            if (revision == current) continue;
            Uri uri = findChild(revisionName(revision));
            long size = uri == null ? 0 : readText(uri)
                    .getBytes(StandardCharsets.UTF_8).length;
            out.add(new RecoveryCandidate(
                    revision == previous
                            ? RecoveryCandidate.Kind.LAST_VALID_REVISION
                            : RecoveryCandidate.Kind.OLDER_REVISION,
                    revision,
                    size,
                    uri == null ? 0 : lastModified(uri)
            ));
        }
        return out;
    }

    @Override
    public synchronized boolean deleteRecoveryRevision(long revision)
            throws IOException {
        if (revision <= 0) return false;
        long current = valueOr(
                readRefRecoverable("current.ref"),
                -1
        );
        long previous = valueOr(
                readRefRecoverable("previous.ref"),
                -1
        );
        if (revision == current || revision == previous) {
            return false;
        }

        Uri target = findChild(revisionName(revision));
        if (target == null) return false;
        try {
            return DocumentsContract.deleteDocument(
                    resolver,
                    target
            );
        } catch (RuntimeException error) {
            throw new IOException(
                    "SAF recovery delete failed",
                    error
            );
        }
    }

    public synchronized Uri treeUri() {
        return treeUri;
    }

    private void publishRevision(ProjectState state) throws IOException {
        String finalName = revisionName(state.revision());
        if (findChild(finalName) != null) {
            throw new IOException("revision already exists");
        }
        String pendingName = finalName + ".pending";
        deleteIfExists(pendingName);

        Uri pending = createChild(pendingName);
        String encoded = codec.encode(state);
        writeText(pending, encoded);

        String reread = readText(pending);
        ProjectState verified;
        try {
            verified = codec.decode(reread);
        } catch (IllegalArgumentException error) {
            throw new IOException("staged SAF revision invalid", error);
        }
        if (!state.equals(verified)) {
            throw new IOException("staged SAF revision verification failed");
        }

        Uri renamed = DocumentsContract.renameDocument(
                resolver,
                pending,
                finalName
        );
        if (renamed == null || findChild(finalName) == null) {
            throw new IOException("SAF revision publish failed");
        }
    }

    private void replaceRef(String name, long revision) throws IOException {
        String pendingName = name + ".pending";
        deleteIfExists(pendingName);
        Uri pending = createChild(pendingName);
        String payload = Long.toString(revision) + "\n";
        writeText(pending, payload);
        if (!payload.equals(readText(pending))) {
            throw new IOException("SAF ref verification failed");
        }

        deleteIfExists(name);
        Uri renamed = DocumentsContract.renameDocument(
                resolver,
                pending,
                name
        );
        if (renamed == null) {
            throw new IOException("SAF ref publish failed");
        }
    }

    private Long readRefRecoverable(String name) throws IOException {
        Uri current = findChild(name);
        if (current != null) return parseRef(readText(current));

        Uri pending = findChild(name + ".pending");
        if (pending == null) return null;

        long revision = parseRef(readText(pending));
        Uri renamed = DocumentsContract.renameDocument(
                resolver,
                pending,
                name
        );
        if (renamed == null) {
            throw new IOException("SAF ref recovery failed");
        }
        return revision;
    }

    private long parseRef(String value) throws IOException {
        try {
            long revision = Long.parseLong(value.trim());
            if (revision <= 0) throw new NumberFormatException();
            return revision;
        } catch (NumberFormatException error) {
            throw new IOException("SAF ref invalid", error);
        }
    }

    private List<Long> listRevisionIds() throws IOException {
        List<Long> out = new ArrayList<>();
        for (Child child : listChildren()) {
            String name = child.name;
            if (name.startsWith("revision-")
                    && name.endsWith(".tbx")) {
                String number = name.substring(
                        "revision-".length(),
                        name.length() - ".tbx".length()
                );
                if (number.matches("\\d+")) {
                    out.add(Long.parseLong(number));
                }
            }
        }
        return out;
    }

    private void trimOldRevisions() throws IOException {
        List<Long> ids = listRevisionIds();
        ids.sort(Comparator.naturalOrder());
        long current = valueOr(readRefRecoverable("current.ref"), -1);
        long previous = valueOr(readRefRecoverable("previous.ref"), -1);

        while (ids.size() > MAX_REVISIONS) {
            long candidate = ids.remove(0);
            if (candidate == current || candidate == previous) continue;
            deleteIfExists(revisionName(candidate));
        }
    }

    private Uri findChild(String name) throws IOException {
        for (Child child : listChildren()) {
            if (name.equals(child.name)) return child.uri;
        }
        return null;
    }

    private long lastModified(Uri uri) {
        String[] projection = {
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = resolver.query(
                uri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor != null
                    && cursor.moveToFirst()
                    && !cursor.isNull(0)) {
                return Math.max(0, cursor.getLong(0));
            }
        } catch (RuntimeException ignored) {
            // Timestamp is descriptive metadata only; integrity does not
            // depend on provider timestamp support.
        }
        return 0;
    }

    private Uri createChild(String name) throws IOException {
        Uri uri = DocumentsContract.createDocument(
                resolver,
                parentDocumentUri,
                MIME,
                name
        );
        if (uri == null) {
            throw new IOException("cannot create SAF document:" + name);
        }
        return uri;
    }

    private void deleteIfExists(String name) throws IOException {
        Uri uri = findChild(name);
        if (uri != null && !DocumentsContract.deleteDocument(resolver, uri)) {
            throw new IOException("cannot delete SAF document:" + name);
        }
    }

    private List<Child> listChildren() throws IOException {
        String parentId = DocumentsContract.getDocumentId(
                parentDocumentUri
        );
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentId
        );
        List<Child> out = new ArrayList<>();
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = resolver.query(
                children,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                throw new IOException("SAF tree query unavailable");
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String displayName = cursor.getString(1);
                Uri documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                documentId
                        );
                out.add(new Child(displayName, documentUri));
            }
        } catch (RuntimeException error) {
            throw new IOException("SAF tree access failed", error);
        }
        return out;
    }

    private String readText(Uri uri) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("SAF input unavailable");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 32 * 1024 * 1024) {
                    throw new IOException("SAF project exceeds size budget");
                }
                output.write(buffer, 0, read);
            }
            return new String(
                    output.toByteArray(),
                    StandardCharsets.UTF_8
            );
        }
    }

    private void writeText(Uri uri, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32 * 1024 * 1024) {
            throw new IOException("SAF project exceeds size budget");
        }
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("SAF output unavailable");
            output.write(bytes);
            output.flush();
        }
    }

    private static Uri rootDocumentUri(Uri treeUri) {
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw new IllegalArgumentException("SAF tree URI required");
        }
        String rootDocumentId =
                DocumentsContract.getTreeDocumentId(treeUri);
        return DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                rootDocumentId
        );
    }

    private static String revisionName(long revision) {
        return "revision-" + revision + ".tbx";
    }

    private static long valueOr(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static final class Child {
        final String name;
        final Uri uri;
        Child(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }
}
