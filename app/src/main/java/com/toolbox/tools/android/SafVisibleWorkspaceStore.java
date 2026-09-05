package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.toolbox.tools.core.FileVisibleWorkspaceStore;
import com.toolbox.tools.core.VisibleWorkspaceStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SafVisibleWorkspaceStore implements VisibleWorkspaceStore {
    private static final int MAX_BYTES = 128 * 1024 * 1024;
    private static final String MIME_BINARY = "application/octet-stream";

    private final ContentResolver resolver;
    private final Uri treeUri;

    public SafVisibleWorkspaceStore(
            ContentResolver resolver,
            Uri treeUri
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.treeUri = Objects.requireNonNull(treeUri, "treeUri");
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw new IllegalArgumentException("SAF tree URI required");
        }
    }

    @Override
    public synchronized void ensureLayout() throws IOException {
        for (Area area : Area.values()) {
            directoryUri(area);
        }
    }

    public synchronized Uri directoryUri(Area area) throws IOException {
        Objects.requireNonNull(area, "area");
        Uri root = rootDocument();
        Uri found = findChild(root, area.folder(), true);
        if (found != null) return found;
        Uri created = DocumentsContract.createDocument(
                resolver,
                root,
                DocumentsContract.Document.MIME_TYPE_DIR,
                area.folder()
        );
        if (created == null) {
            throw new IOException(
                    "cannot create SAF workspace directory:" + area.folder()
            );
        }
        return created;
    }

    @Override
    public synchronized void write(
            Area area,
            String name,
            byte[] bytes
    ) throws IOException {
        if (bytes == null) throw new NullPointerException("bytes");
        writeStream(
                area,
                name,
                new ByteArrayInputStream(bytes),
                MAX_BYTES
        );
    }

    @Override
    public synchronized WriteResult writeStream(
            Area area,
            String name,
            InputStream input,
            long maxBytes
    ) throws IOException {
        if (input == null) throw new NullPointerException("input");
        if (maxBytes <= 0 || maxBytes > MAX_BYTES) {
            throw new IOException("visible stream budget invalid");
        }

        String safe = FileVisibleWorkspaceStore.safeName(name);
        Uri parent = directoryUri(area);
        String pendingName = safe + ".pending";
        deleteIfExists(parent, pendingName);

        Uri pending = DocumentsContract.createDocument(
                resolver,
                parent,
                MIME_BINARY,
                pendingName
        );
        if (pending == null) {
            throw new IOException("cannot create SAF pending item");
        }

        StreamDigest written;
        try {
            written = writeStreamToUri(
                    pending,
                    input,
                    maxBytes
            );
            StreamDigest verified = digestUri(
                    pending,
                    maxBytes
            );
            if (written.bytes != verified.bytes
                    || !written.sha256.equals(verified.sha256)) {
                throw new IOException(
                        "SAF visible item verification failed"
                );
            }

            deleteIfExists(parent, safe);
            Uri published = DocumentsContract.renameDocument(
                    resolver,
                    pending,
                    safe
            );
            if (published == null) {
                Uri target = DocumentsContract.createDocument(
                        resolver,
                        parent,
                        MIME_BINARY,
                        safe
                );
                if (target == null) {
                    throw new IOException(
                            "cannot publish SAF visible item"
                    );
                }
                try (InputStream pendingInput =
                             resolver.openInputStream(pending)) {
                    if (pendingInput == null) {
                        throw new IOException(
                                "SAF pending input unavailable"
                        );
                    }
                    StreamDigest copied = writeStreamToUri(
                            target,
                            pendingInput,
                            maxBytes
                    );
                    StreamDigest targetVerified = digestUri(
                            target,
                            maxBytes
                    );
                    if (copied.bytes != written.bytes
                            || !copied.sha256.equals(written.sha256)
                            || targetVerified.bytes != written.bytes
                            || !targetVerified.sha256.equals(
                                    written.sha256
                            )) {
                        DocumentsContract.deleteDocument(
                                resolver,
                                target
                        );
                        throw new IOException(
                                "SAF publish verification failed"
                        );
                    }
                }
                DocumentsContract.deleteDocument(
                        resolver,
                        pending
                );
            }
            return new WriteResult(
                    written.bytes,
                    written.sha256
            );
        } catch (IOException | RuntimeException error) {
            try {
                DocumentsContract.deleteDocument(
                        resolver,
                        pending
                );
            } catch (RuntimeException ignored) {
                // Best-effort cleanup; original error remains authoritative.
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(
                    "SAF visible stream write failed",
                    error
            );
        }
    }

    @Override
    public synchronized byte[] read(Area area, String name)
            throws IOException {
        Uri uri = findChild(
                directoryUri(area),
                FileVisibleWorkspaceStore.safeName(name),
                false
        );
        if (uri == null) throw new IOException("visible item missing");
        return readBytes(uri);
    }

    @Override
    public synchronized InputStream openInputStream(
            Area area,
            String name
    ) throws IOException {
        Uri uri = findChild(
                directoryUri(area),
                FileVisibleWorkspaceStore.safeName(name),
                false
        );
        if (uri == null) throw new IOException("visible item missing");
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("SAF input unavailable");
        return input;
    }

    public synchronized Uri itemUri(Area area, String name)
            throws IOException {
        Uri uri = findChild(
                directoryUri(area),
                FileVisibleWorkspaceStore.safeName(name),
                false
        );
        if (uri == null) throw new IOException("visible item missing");
        return uri;
    }

    @Override
    public synchronized boolean exists(Area area, String name)
            throws IOException {
        return findChild(
                directoryUri(area),
                FileVisibleWorkspaceStore.safeName(name),
                false
        ) != null;
    }

    @Override
    public synchronized List<String> list(Area area) throws IOException {
        Uri parent = directoryUri(area);
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentId
        );
        List<String> out = new ArrayList<>();
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(
                children,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                throw new IOException("SAF workspace query unavailable");
            }
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String mime = cursor.getString(1);
                if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)
                        && name != null
                        && !name.endsWith(".pending")) {
                    out.add(name);
                }
            }
        } catch (RuntimeException error) {
            throw new IOException("SAF workspace list failed", error);
        }
        Collections.sort(out);
        return Collections.unmodifiableList(out);
    }

    private Uri rootDocument() {
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        return DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                rootId
        );
    }

    private Uri findChild(
            Uri parent,
            String name,
            boolean directory
    ) throws IOException {
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentId
        );
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(
                children,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                throw new IOException("SAF workspace query unavailable");
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String displayName = cursor.getString(1);
                String mime = cursor.getString(2);
                boolean isDirectory =
                        DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                if (name.equals(displayName) && directory == isDirectory) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            documentId
                    );
                }
            }
        } catch (RuntimeException error) {
            throw new IOException("SAF workspace access failed", error);
        }
        return null;
    }

    private void deleteIfExists(Uri parent, String name) throws IOException {
        Uri existing = findChild(parent, name, false);
        if (existing != null
                && !DocumentsContract.deleteDocument(resolver, existing)) {
            throw new IOException("cannot delete SAF item:" + name);
        }
    }

    private byte[] readBytes(Uri uri) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("SAF input unavailable");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("SAF visible item exceeds budget");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (RuntimeException error) {
            throw new IOException("SAF read failed", error);
        }
    }

    private StreamDigest writeStreamToUri(
            Uri uri,
            InputStream input,
            long maxBytes
    ) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (OutputStream output =
                     resolver.openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IOException("SAF output unavailable");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(
                            "SAF visible item exceeds budget"
                    );
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (RuntimeException error) {
            throw new IOException("SAF write failed", error);
        }
        return new StreamDigest(
                total,
                hex(digest.digest())
        );
    }

    private StreamDigest digestUri(
            Uri uri,
            long maxBytes
    ) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("SAF input unavailable");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(
                            "SAF visible item exceeds budget"
                    );
                }
                digest.update(buffer, 0, read);
            }
        } catch (RuntimeException error) {
            throw new IOException("SAF read failed", error);
        }
        return new StreamDigest(
                total,
                hex(digest.digest())
        );
    }

    private void writeBytes(Uri uri, byte[] bytes) throws IOException {
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("SAF output unavailable");
            output.write(bytes);
            output.flush();
        } catch (RuntimeException error) {
            throw new IOException("SAF write failed", error);
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        MessageDigest digest = sha256Digest();
        return hex(digest.digest(bytes));
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 unavailable", error);
        }
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

    private static final class StreamDigest {
        final long bytes;
        final String sha256;

        StreamDigest(long bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }
}
