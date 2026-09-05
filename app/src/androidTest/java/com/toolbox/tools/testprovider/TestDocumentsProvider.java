package com.toolbox.tools.testprovider;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class TestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY =
            "com.toolbox.tools.testdocuments";
    public static final String ROOT_ID = "root";

    private static final class Node {
        final String id;
        final String parentId;
        String displayName;
        final String mime;
        final File file;

        Node(
                String id,
                String parentId,
                String displayName,
                String mime,
                File file
        ) {
            this.id = id;
            this.parentId = parentId;
            this.displayName = displayName;
            this.mime = mime;
            this.file = file;
        }

        boolean directory() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public boolean onCreate() {
        File rootFile = new File(
                getContext().getFilesDir(),
                "documents-provider"
        );
        if (!rootFile.isDirectory()) rootFile.mkdirs();
        nodes.clear();
        nodes.put(
                ROOT_ID,
                new Node(
                        ROOT_ID,
                        null,
                        "ToolBox Test Root",
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        rootFile
                )
        );
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null
                ? projection
                : new String[] {
                        DocumentsContract.Root.COLUMN_ROOT_ID,
                        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Root.COLUMN_TITLE,
                        DocumentsContract.Root.COLUMN_FLAGS,
                        DocumentsContract.Root.COLUMN_MIME_TYPES
                };
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (DocumentsContract.Root.COLUMN_ROOT_ID.equals(column)) {
                row.add(ROOT_ID);
            } else if (DocumentsContract.Root.COLUMN_DOCUMENT_ID.equals(column)) {
                row.add(ROOT_ID);
            } else if (DocumentsContract.Root.COLUMN_TITLE.equals(column)) {
                row.add("ToolBox Test Root");
            } else if (DocumentsContract.Root.COLUMN_FLAGS.equals(column)) {
                row.add(
                        DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                                | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                );
            } else if (DocumentsContract.Root.COLUMN_MIME_TYPES.equals(column)) {
                row.add("*/*");
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Cursor queryDocument(
            String documentId,
            String[] projection
    ) throws FileNotFoundException {
        Node node = require(documentId);
        MatrixCursor cursor = new MatrixCursor(
                projection != null
                        ? projection
                        : defaultDocumentProjection()
        );
        includeDocument(cursor, node);
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId,
            String[] projection,
            String sortOrder
    ) throws FileNotFoundException {
        require(parentDocumentId);
        MatrixCursor cursor = new MatrixCursor(
                projection != null
                        ? projection
                        : defaultDocumentProjection()
        );
        for (Node node : new ArrayList<>(nodes.values())) {
            if (parentDocumentId.equals(node.parentId)) {
                includeDocument(cursor, node);
            }
        }
        return cursor;
    }

    @Override
    public String createDocument(
            String parentDocumentId,
            String mimeType,
            String displayName
    ) throws FileNotFoundException {
        Node parent = require(parentDocumentId);
        if (!parent.directory()) {
            throw new FileNotFoundException("parent is not directory");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new FileNotFoundException("display name missing");
        }

        String id = "doc-" + ids.incrementAndGet();
        boolean directory =
                DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
        File file = new File(parent.file, id);
        try {
            if (directory) {
                if (!file.mkdir()) {
                    throw new FileNotFoundException(
                            "cannot create directory"
                    );
                }
            } else if (!file.createNewFile()) {
                throw new FileNotFoundException(
                        "cannot create file"
                );
            }
        } catch (java.io.IOException error) {
            FileNotFoundException wrapped =
                    new FileNotFoundException(error.getMessage());
            wrapped.initCause(error);
            throw wrapped;
        }

        Node node = new Node(
                id,
                parentDocumentId,
                displayName,
                mimeType,
                file
        );
        nodes.put(id, node);
        return id;
    }

    @Override
    public String renameDocument(
            String documentId,
            String displayName
    ) throws FileNotFoundException {
        Node node = require(documentId);
        node.displayName = displayName;
        return documentId;
    }

    @Override
    public void deleteDocument(String documentId)
            throws FileNotFoundException {
        if (ROOT_ID.equals(documentId)) {
            throw new FileNotFoundException("cannot delete root");
        }
        Node node = require(documentId);
        deleteRecursive(documentId, node.file);
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId,
            String mode,
            CancellationSignal signal
    ) throws FileNotFoundException {
        Node node = require(documentId);
        if (node.directory()) {
            throw new FileNotFoundException("cannot open directory");
        }
        int flags = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(node.file, flags);
    }

    @Override
    public boolean isChildDocument(
            String parentDocumentId,
            String documentId
    ) {
        String current = documentId;
        while (current != null) {
            if (parentDocumentId.equals(current)) return true;
            Node node = nodes.get(current);
            current = node == null ? null : node.parentId;
        }
        return false;
    }

    private Node require(String id) throws FileNotFoundException {
        Node node = nodes.get(id);
        if (node == null) {
            throw new FileNotFoundException("unknown document:" + id);
        }
        return node;
    }

    private void includeDocument(
            MatrixCursor cursor,
            Node node
    ) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) {
                row.add(node.id);
            } else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) {
                row.add(node.displayName);
            } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) {
                row.add(node.mime);
            } else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) {
                row.add(node.directory() ? 0L : node.file.length());
            } else if (DocumentsContract.Document.COLUMN_FLAGS.equals(column)) {
                int flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                        | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                        | DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
                if (node.directory()) {
                    flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
                }
                row.add(flags);
            } else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column)) {
                row.add(node.file.lastModified());
            } else {
                row.add(null);
            }
        }
    }

    private String[] defaultDocumentProjection() {
        return new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
    }

    private void deleteRecursive(String id, File file) {
        List<String> children = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (id.equals(node.parentId)) children.add(node.id);
        }
        for (String child : children) {
            Node node = nodes.get(child);
            if (node != null) deleteRecursive(child, node.file);
        }
        if (file.isDirectory()) {
            File[] nested = file.listFiles();
            if (nested != null) {
                for (File item : nested) item.delete();
            }
        }
        file.delete();
        nodes.remove(id);
    }
}
