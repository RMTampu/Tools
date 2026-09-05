package com.toolbox.tools.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.toolbox.tools.core.ProjectAccessStatus;
import com.toolbox.tools.core.ProjectDefinitionCodec;
import com.toolbox.tools.core.ProjectLoadResult;
import com.toolbox.tools.core.ProjectManifest;
import com.toolbox.tools.core.ProjectResourceLayout;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectStore;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.RecoveryCandidate;
import com.toolbox.tools.core.StableId;
import com.toolbox.tools.core.StaleWriteException;
import com.toolbox.tools.library.LibraryDependencyLock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User-owned SAF Project Store.
 *
 * V2 revisions are hierarchical packages:
 * revision-N/
 *   project.json
 *   project.manifest
 *   project.index
 *   screens/<screen>/*.res
 *   logic/*.res
 *   data/*.res
 *   bindings/*.res
 *   assets/*.res
 *   styles/*.res
 *   localization/*.res
 *   metadata/*.res
 *
 * Legacy revision-N.tbx blobs remain readable as rollback candidates and are
 * naturally migrated when a later V2 revision is committed.
 */
public final class SafProjectStore implements ProjectStore {
    private static final String MIME_FILE =
            "application/octet-stream";
    private static final String MIME_DIR =
            DocumentsContract.Document.MIME_TYPE_DIR;
    private static final int MAX_REVISIONS = 32;
    private static final int MAX_TEXT_BYTES = 32 * 1024 * 1024;
    private static final ConcurrentHashMap<String, Object> LOCKS =
            new ConcurrentHashMap<>();

    private final ContentResolver resolver;
    private final Uri treeUri;
    private final Uri parentDocumentUri;
    private final ProjectDefinitionCodec definitionCodec =
            new ProjectDefinitionCodec();
    private final ProjectCodec legacyCodec = new ProjectCodec();
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
        this.resolver = Objects.requireNonNull(
                resolver,
                "resolver"
        );
        this.treeUri = Objects.requireNonNull(
                treeUri,
                "treeUri"
        );
        this.parentDocumentUri = Objects.requireNonNull(
                parentDocumentUri,
                "parentDocumentUri"
        );
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw new IllegalArgumentException(
                    "SAF tree URI required"
            );
        }
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        String parentId =
                DocumentsContract.getDocumentId(parentDocumentUri);
        if (treeId == null || parentId == null) {
            throw new IllegalArgumentException(
                    "SAF project directory identity required"
            );
        }
    }

    @Override
    public ProjectLoadResult load(String projectId)
            throws IOException {
        synchronized (storeLock()) {
            StableId.require(projectId, "projectId");
            recoverInterruptedTransaction();
            Long current = readRefRecoverable("current.ref");
            if (current == null) {
                return new ProjectLoadResult(
                        ProjectAccessStatus.FOLDER_MISSING,
                        null,
                        recoveryCandidatesLocked()
                );
            }
            try {
                ProjectState state = loadRevisionLocked(current);
                if (!projectId.equals(state.projectId())) {
                    return new ProjectLoadResult(
                            ProjectAccessStatus.PROJECT_CORRUPT,
                            null,
                            recoveryCandidatesLocked()
                    );
                }
                if (state.schemaVersion()
                        != ProjectState.CURRENT_SCHEMA_VERSION) {
                    return new ProjectLoadResult(
                            ProjectAccessStatus.SCHEMA_INCOMPATIBLE,
                            state,
                            recoveryCandidatesLocked()
                    );
                }
                return new ProjectLoadResult(
                        ProjectAccessStatus.PROJECT_OK,
                        state,
                        recoveryCandidatesLocked()
                );
            } catch (IOException error) {
                ProjectState fallback = null;
                Long previous = readRefRecoverable(
                        "previous.ref"
                );
                if (previous != null) {
                    try {
                        fallback = loadRevisionLocked(previous);
                    } catch (IOException ignored) {
                        fallback = null;
                    }
                }
                return new ProjectLoadResult(
                        ProjectAccessStatus.PROJECT_CORRUPT,
                        fallback,
                        recoveryCandidatesLocked()
                );
            }
        }
    }

    @Override
    public ProjectState commit(
            ProjectState workingState,
            long expectedRevision
    ) throws IOException {
        synchronized (storeLock()) {
            ProjectValidationResult validation =
                    validator.validate(workingState);
            if (!validation.isPass()) {
                throw new IOException(
                        "PROJECT_VALIDATION_FAILED:"
                                + validation.message()
                );
            }
            recoverInterruptedTransaction();

            Long current = readRefRecoverable("current.ref");
            long actual = current == null ? 0 : current;
            if (actual != expectedRevision) {
                throw new StaleWriteException(
                        expectedRevision,
                        actual
                );
            }

            long nextRevision = actual + 1;
            ProjectState committed =
                    workingState.withRevision(nextRevision);
            writeJournal(nextRevision, actual);
            Uri pending = null;
            try {
                pending = stageRevision(committed);

                // Optimistic CAS re-check closes multi-instance races
                // inside this app process before publish.
                long recheck = valueOr(
                        readRefRecoverable("current.ref"),
                        0
                );
                if (recheck != actual) {
                    throw new StaleWriteException(
                            expectedRevision,
                            recheck
                    );
                }

                Uri finalDir = DocumentsContract.renameDocument(
                        resolver,
                        pending,
                        revisionName(nextRevision)
                );
                pending = null;
                if (finalDir == null
                        || findChild(
                                parentDocumentUri,
                                revisionName(nextRevision),
                                true
                        ) == null) {
                    throw new IOException(
                            "SAF revision directory publish failed"
                    );
                }

                if (actual > 0) {
                    replaceRef("previous.ref", actual);
                }
                replaceRef("current.ref", nextRevision);
                deleteIfExists(
                        parentDocumentUri,
                        "journal.pending",
                        false
                );
                trimOldRevisions();
                return committed;
            } catch (IOException | RuntimeException error) {
                if (pending != null) {
                    try {
                        DocumentsContract.deleteDocument(
                                resolver,
                                pending
                        );
                    } catch (RuntimeException ignored) {
                        // Journal recovery handles remaining pending dir.
                    }
                }
                if (error instanceof IOException) {
                    throw (IOException) error;
                }
                throw error;
            }
        }
    }

    @Override
    public ProjectState recoverRevision(long revision)
            throws IOException {
        synchronized (storeLock()) {
            return recoverStateLocked(
                    loadRevisionLocked(revision)
            );
        }
    }

    @Override
    public ProjectState recoverState(ProjectState candidate)
            throws IOException {
        synchronized (storeLock()) {
            return recoverStateLocked(candidate);
        }
    }

    private ProjectState recoverStateLocked(ProjectState candidate)
            throws IOException {
        ProjectValidationResult validation =
                validator.validate(candidate);
        if (!validation.isPass()) {
            throw new IOException(
                    "RECOVERY_CANDIDATE_INVALID:"
                            + validation.message()
            );
        }
        Long current = readRefRecoverable("current.ref");
        long actual = current == null ? 0 : current;
        return commit(candidate, actual);
    }

    @Override
    public ProjectState loadRevision(long revision)
            throws IOException {
        synchronized (storeLock()) {
            return loadRevisionLocked(revision);
        }
    }

    private ProjectState loadRevisionLocked(long revision)
            throws IOException {
        if (revision <= 0) {
            throw new IOException("revision invalid");
        }

        Uri directory = findChild(
                parentDocumentUri,
                revisionName(revision),
                true
        );
        if (directory != null) {
            return readRevisionDirectory(
                    directory,
                    revision
            );
        }

        Uri legacy = findChild(
                parentDocumentUri,
                legacyRevisionName(revision),
                false
        );
        if (legacy == null) {
            throw new IOException(
                    "revision missing:" + revision
            );
        }
        try {
            ProjectState state = legacyCodec.decode(
                    readText(legacy, MAX_TEXT_BYTES)
            );
            if (state.revision() != revision) {
                throw new IOException(
                        "legacy revision identity mismatch"
                );
            }
            ProjectValidationResult validation =
                    validator.validate(state);
            if (!validation.isPass()
                    && state.schemaVersion()
                        == ProjectState.CURRENT_SCHEMA_VERSION) {
                throw new IOException(
                        "legacy stored project invalid:"
                                + validation.message()
                );
            }
            return state;
        } catch (IllegalArgumentException error) {
            throw new IOException(
                    "legacy revision corrupt",
                    error
            );
        }
    }

    @Override
    public List<RecoveryCandidate> recoveryCandidates()
            throws IOException {
        synchronized (storeLock()) {
            return recoveryCandidatesLocked();
        }
    }

    private List<RecoveryCandidate> recoveryCandidatesLocked()
            throws IOException {
        long current = valueOr(
                readRefRecoverable("current.ref"),
                -1
        );
        long previous = valueOr(
                readRefRecoverable("previous.ref"),
                -1
        );
        List<Long> revisions = listRevisionIds();
        revisions.sort(Comparator.reverseOrder());

        List<RecoveryCandidate> out = new ArrayList<>();
        for (long revision : revisions) {
            if (revision == current) continue;
            Uri directory = findChild(
                    parentDocumentUri,
                    revisionName(revision),
                    true
            );
            Uri legacy = directory == null
                    ? findChild(
                            parentDocumentUri,
                            legacyRevisionName(revision),
                            false
                    )
                    : null;
            Uri source = directory != null
                    ? directory
                    : legacy;
            long size = source == null
                    ? 0
                    : documentSizeRecursive(source, 0);
            long createdAt = source == null
                    ? 0
                    : lastModified(source);

            out.add(new RecoveryCandidate(
                    revision == previous
                            ? RecoveryCandidate.Kind.LAST_VALID_REVISION
                            : RecoveryCandidate.Kind.OLDER_REVISION,
                    revision,
                    size,
                    createdAt
            ));
        }
        return out;
    }

    @Override
    public boolean deleteRecoveryRevision(long revision)
            throws IOException {
        synchronized (storeLock()) {
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

            Uri target = findChild(
                    parentDocumentUri,
                    revisionName(revision),
                    true
            );
            if (target == null) {
                target = findChild(
                        parentDocumentUri,
                        legacyRevisionName(revision),
                        false
                );
            }
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
    }

    public Uri treeUri() {
        return treeUri;
    }

    private Uri stageRevision(ProjectState state)
            throws IOException {
        String finalName = revisionName(state.revision());
        if (findChild(parentDocumentUri, finalName, true) != null
                || findChild(
                        parentDocumentUri,
                        legacyRevisionName(state.revision()),
                        false
                ) != null) {
            throw new IOException(
                    "revision already exists"
            );
        }

        String pendingName = finalName + ".pending";
        deleteIfExists(
                parentDocumentUri,
                pendingName,
                true
        );
        Uri pending = createDirectory(
                parentDocumentUri,
                pendingName
        );

        String definition = definitionCodec.encode(state);
        ProjectManifest manifest = ProjectManifest.from(
                state,
                definition
        );

        writeNamedText(
                pending,
                "project.json",
                definition
        );

        for (Map.Entry<String, String> entry
                : state.resources().entrySet()) {
            String relative =
                    ProjectResourceLayout.relativePath(
                            entry.getKey()
                    );
            Uri file = ensureFilePath(
                    pending,
                    relative
            );
            writeText(
                    file,
                    entry.getValue()
            );
        }

        String dependencyLock = state.resources().get(
                LibraryDependencyLock.PROJECT_RESOURCE_ID
        );
        if (dependencyLock != null) {
            LibraryDependencyLock decoded =
                    LibraryDependencyLock.decode(
                            dependencyLock
                    );
            if (decoded.projectSchemaVersion()
                    != state.schemaVersion()
                    || decoded.buildModelVersion()
                        != state.buildModelVersion()) {
                throw new IOException(
                        "dependency.lock version mismatch"
                );
            }
            writeNamedText(
                    pending,
                    "dependency.lock",
                    dependencyLock
            );
        }

        writeNamedText(
                pending,
                "project.manifest",
                manifest.encode()
        );
        writeNamedText(
                pending,
                "project.index",
                buildIndex(state)
        );

        ProjectState verified = readRevisionDirectory(
                pending,
                state.revision()
        );
        if (!state.equals(verified)) {
            throw new IOException(
                    "staged SAF V2 revision verification failed"
            );
        }
        return pending;
    }

    private ProjectState readRevisionDirectory(
            Uri revisionDir,
            long expectedRevision
    ) throws IOException {
        Uri projectFile = requireChild(
                revisionDir,
                "project.json",
                false
        );
        Uri manifestFile = requireChild(
                revisionDir,
                "project.manifest",
                false
        );
        Uri indexFile = requireChild(
                revisionDir,
                "project.index",
                false
        );

        String definition = readText(
                projectFile,
                MAX_TEXT_BYTES
        );
        String encodedManifest = readText(
                manifestFile,
                MAX_TEXT_BYTES
        );
        String index = readText(
                indexFile,
                MAX_TEXT_BYTES
        );

        try {
            Map<String, String> resources =
                    readIndexedResources(
                            revisionDir,
                            index
                    );
            verifyDependencyLock(
                    revisionDir,
                    resources
            );
            ProjectState state = definitionCodec.decode(
                    definition,
                    resources
            );
            if (state.revision() != expectedRevision) {
                throw new IOException(
                        "revision directory identity mismatch"
                );
            }
            ProjectManifest manifest =
                    ProjectManifest.decode(
                            encodedManifest
                    );
            if (!manifest.verifies(state, definition)) {
                throw new IOException(
                        "SAF manifest verification failed"
                );
            }
            ProjectValidationResult validation =
                    validator.validate(state);
            if (!validation.isPass()
                    && state.schemaVersion()
                        == ProjectState.CURRENT_SCHEMA_VERSION) {
                throw new IOException(
                        "stored SAF project invalid:"
                                + validation.message()
                );
            }
            verifyRevisionRootLayout(revisionDir, resources);
            return state;
        } catch (IllegalArgumentException error) {
            throw new IOException(
                    "SAF revision corrupt",
                    error
            );
        }
    }

    private Map<String, String> readIndexedResources(
            Uri revisionDir,
            String index
    ) throws IOException {
        String[] lines = index.split("\n");
        if (lines.length < 4
                || !"TBX_PROJECT_INDEX_V2".equals(lines[0])) {
            throw new IOException(
                    "SAF project index header invalid"
            );
        }

        int declaredCount = -1;
        Map<String, String> resources =
                new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("resourceCount=")) {
                declaredCount = integer(
                        line.substring(
                                "resourceCount=".length()
                        )
                );
            } else if (line.startsWith("resource=")) {
                String value = line.substring(
                        "resource=".length()
                );
                int separator = value.indexOf('|');
                if (separator <= 0
                        || separator == value.length() - 1) {
                    throw new IOException(
                            "SAF project index resource invalid"
                    );
                }
                String id = value.substring(
                        0,
                        separator
                );
                StableId.require(id, "resourceId");
                String relative = value.substring(
                        separator + 1
                );
                if (!ProjectResourceLayout.validRelativePath(
                        id,
                        relative
                )) {
                    throw new IOException(
                            "SAF project index path mismatch"
                    );
                }
                Uri resource = findPath(
                        revisionDir,
                        relative
                );
                if (resource == null) {
                    throw new IOException(
                            "SAF indexed resource missing:"
                                    + id
                    );
                }
                String payload = readText(
                        resource,
                        ProjectState.MAX_RESOURCE_BYTES
                );
                if (resources.put(id, payload) != null) {
                    throw new IOException(
                            "SAF duplicate indexed resource"
                    );
                }
            }
        }

        if (declaredCount < 0
                || declaredCount != resources.size()) {
            throw new IOException(
                    "SAF project index count mismatch"
            );
        }
        if (countDomainFiles(revisionDir)
                != resources.size()) {
            throw new IOException(
                    "SAF unexpected resource payload"
            );
        }
        return resources;
    }

    private void verifyDependencyLock(
            Uri revisionDir,
            Map<String, String> resources
    ) throws IOException {
        String expected = resources.get(
                LibraryDependencyLock.PROJECT_RESOURCE_ID
        );
        Uri file = findChild(
                revisionDir,
                "dependency.lock",
                false
        );
        if (expected == null) {
            if (file != null) {
                throw new IOException(
                        "orphan SAF dependency.lock"
                );
            }
            return;
        }
        if (file == null) {
            throw new IOException(
                    "SAF dependency.lock missing"
            );
        }
        String persisted = readText(
                file,
                ProjectState.MAX_RESOURCE_BYTES
        );
        if (!expected.equals(persisted)) {
            throw new IOException(
                    "SAF dependency.lock resource mismatch"
            );
        }
        try {
            LibraryDependencyLock.decode(persisted);
        } catch (IllegalArgumentException error) {
            throw new IOException(
                    "SAF dependency.lock invalid",
                    error
            );
        }
    }

    private void verifyRevisionRootLayout(
            Uri revisionDir,
            Map<String, String> resources
    ) throws IOException {
        java.util.Set<String> allowed =
                new java.util.LinkedHashSet<>();
        allowed.add("project.json");
        allowed.add("project.manifest");
        allowed.add("project.index");
        if (resources.containsKey(
                LibraryDependencyLock.PROJECT_RESOURCE_ID
        )) {
            allowed.add("dependency.lock");
        }
        allowed.addAll(
                ProjectResourceLayout.domainDirectories()
        );

        for (Child child : listChildren(revisionDir)) {
            if (!allowed.contains(child.name)) {
                throw new IOException(
                        "unexpected SAF revision payload:"
                                + child.name
                );
            }
        }
    }

    private long countDomainFiles(Uri revisionDir)
            throws IOException {
        long count = 0;
        for (String domain
                : ProjectResourceLayout.domainDirectories()) {
            Uri directory = findChild(
                    revisionDir,
                    domain,
                    true
            );
            if (directory != null) {
                count += countFilesRecursive(
                        directory,
                        0
                );
            }
        }
        return count;
    }

    private long countFilesRecursive(Uri directory, int depth)
            throws IOException {
        if (depth > 8) {
            throw new IOException(
                    "SAF project nesting too deep"
            );
        }
        long count = 0;
        for (Child child : listChildren(directory)) {
            if (child.directory) {
                count += countFilesRecursive(
                        child.uri,
                        depth + 1
                );
            } else {
                count++;
            }
        }
        return count;
    }

    private void writeJournal(long next, long expected)
            throws IOException {
        String payload = "TBX_SAF_COMMIT_JOURNAL_V1\n"
                + "next=" + next + "\n"
                + "expected=" + expected + "\n";
        replaceTextFile(
                "journal.pending",
                payload,
                false
        );
    }

    private void recoverInterruptedTransaction()
            throws IOException {
        Uri journal = findChild(
                parentDocumentUri,
                "journal.pending",
                false
        );
        if (journal == null) {
            // Pending revision directory without a journal is invalid
            // and can never become current.
            deleteDanglingPendingDirectories();
            return;
        }

        String payload = readText(journal, 4096);
        long next = journalLong(payload, "next");
        long current = valueOr(
                readRefRecoverable("current.ref"),
                0
        );

        Uri pending = findChild(
                parentDocumentUri,
                revisionName(next) + ".pending",
                true
        );
        if (pending != null) {
            DocumentsContract.deleteDocument(
                    resolver,
                    pending
            );
        }

        if (current != next) {
            Uri uncommittedFinal = findChild(
                    parentDocumentUri,
                    revisionName(next),
                    true
            );
            if (uncommittedFinal != null) {
                DocumentsContract.deleteDocument(
                        resolver,
                        uncommittedFinal
                );
            }
        }
        DocumentsContract.deleteDocument(
                resolver,
                journal
        );
        deleteDanglingPendingDirectories();
    }

    private void deleteDanglingPendingDirectories()
            throws IOException {
        for (Child child : listChildren(parentDocumentUri)) {
            if (child.directory
                    && child.name.matches(
                        "revision-[0-9]+\\.pending"
                    )) {
                DocumentsContract.deleteDocument(
                        resolver,
                        child.uri
                );
            }
        }
    }

    private void replaceRef(String name, long revision)
            throws IOException {
        replaceTextFile(
                name,
                Long.toString(revision) + "\n",
                true
        );
    }

    private void replaceTextFile(
            String name,
            String payload,
            boolean recoverPending
    ) throws IOException {
        String pendingName = name + ".pending";
        deleteIfExists(
                parentDocumentUri,
                pendingName,
                false
        );
        Uri pending = createFile(
                parentDocumentUri,
                pendingName
        );
        writeText(pending, payload);
        if (!payload.equals(readText(
                pending,
                MAX_TEXT_BYTES
        ))) {
            throw new IOException(
                    "SAF atomic text verification failed"
            );
        }

        deleteIfExists(
                parentDocumentUri,
                name,
                false
        );
        Uri renamed = DocumentsContract.renameDocument(
                resolver,
                pending,
                name
        );
        if (renamed == null) {
            throw new IOException(
                    "SAF atomic text publish failed:"
                            + name
            );
        }
    }

    private Long readRefRecoverable(String name)
            throws IOException {
        Uri current = findChild(
                parentDocumentUri,
                name,
                false
        );
        if (current != null) {
            return parseRef(
                    readText(current, 128)
            );
        }

        Uri pending = findChild(
                parentDocumentUri,
                name + ".pending",
                false
        );
        if (pending == null) return null;

        long revision = parseRef(
                readText(pending, 128)
        );
        Uri renamed = DocumentsContract.renameDocument(
                resolver,
                pending,
                name
        );
        if (renamed == null) {
            throw new IOException(
                    "SAF ref recovery failed"
            );
        }
        return revision;
    }

    private long parseRef(String value) throws IOException {
        try {
            long revision = Long.parseLong(value.trim());
            if (revision <= 0) {
                throw new NumberFormatException();
            }
            return revision;
        } catch (NumberFormatException error) {
            throw new IOException(
                    "SAF ref invalid",
                    error
            );
        }
    }

    private List<Long> listRevisionIds() throws IOException {
        java.util.Set<Long> unique =
                new java.util.TreeSet<>();
        for (Child child : listChildren(parentDocumentUri)) {
            String name = child.name;
            if (child.directory
                    && name.matches("revision-[0-9]+")) {
                unique.add(Long.parseLong(
                        name.substring(
                                "revision-".length()
                        )
                ));
            } else if (!child.directory
                    && name.startsWith("revision-")
                    && name.endsWith(".tbx")) {
                String number = name.substring(
                        "revision-".length(),
                        name.length() - ".tbx".length()
                );
                if (number.matches("[0-9]+")) {
                    unique.add(
                            Long.parseLong(number)
                    );
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private void trimOldRevisions() throws IOException {
        List<Long> ids = listRevisionIds();
        ids.sort(Comparator.naturalOrder());
        long current = valueOr(
                readRefRecoverable("current.ref"),
                -1
        );
        long previous = valueOr(
                readRefRecoverable("previous.ref"),
                -1
        );

        while (ids.size() > MAX_REVISIONS) {
            long candidate = ids.remove(0);
            if (candidate == current
                    || candidate == previous) {
                continue;
            }
            Uri directory = findChild(
                    parentDocumentUri,
                    revisionName(candidate),
                    true
            );
            if (directory != null) {
                DocumentsContract.deleteDocument(
                        resolver,
                        directory
                );
            } else {
                deleteIfExists(
                        parentDocumentUri,
                        legacyRevisionName(candidate),
                        false
                );
            }
        }
    }

    private String buildIndex(ProjectState state) {
        StringBuilder out = new StringBuilder();
        out.append("TBX_PROJECT_INDEX_V2\n");
        out.append("projectId=")
                .append(state.projectId())
                .append('\n');
        out.append("revision=")
                .append(state.revision())
                .append('\n');
        out.append("resourceCount=")
                .append(state.resources().size())
                .append('\n');
        for (String id : state.resources().keySet()) {
            out.append("resource=")
                    .append(id)
                    .append('|')
                    .append(
                            ProjectResourceLayout.relativePath(id)
                    )
                    .append('\n');
        }
        return out.toString();
    }

    private Uri ensureFilePath(
            Uri root,
            String relativePath
    ) throws IOException {
        String[] parts = relativePath.split("/");
        if (parts.length < 2) {
            throw new IOException(
                    "SAF resource path invalid"
            );
        }
        Uri parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Uri next = findChild(
                    parent,
                    parts[i],
                    true
            );
            if (next == null) {
                next = createDirectory(
                        parent,
                        parts[i]
                );
            }
            parent = next;
        }
        String fileName = parts[parts.length - 1];
        if (findChild(parent, fileName, false) != null) {
            throw new IOException(
                    "SAF resource duplicate:"
                            + relativePath
            );
        }
        return createFile(parent, fileName);
    }

    private Uri findPath(Uri root, String relativePath)
            throws IOException {
        String[] parts = relativePath.split("/");
        Uri current = root;
        for (int i = 0; i < parts.length; i++) {
            boolean directory = i < parts.length - 1;
            current = findChild(
                    current,
                    parts[i],
                    directory
            );
            if (current == null) return null;
        }
        return current;
    }

    private void writeNamedText(
            Uri parent,
            String name,
            String value
    ) throws IOException {
        if (findChild(parent, name, false) != null) {
            throw new IOException(
                    "SAF duplicate file:" + name
            );
        }
        Uri file = createFile(parent, name);
        writeText(file, value);
    }

    private Uri requireChild(
            Uri parent,
            String name,
            boolean directory
    ) throws IOException {
        Uri child = findChild(
                parent,
                name,
                directory
        );
        if (child == null) {
            throw new IOException(
                    "SAF child missing:" + name
            );
        }
        return child;
    }

    private Uri createDirectory(Uri parent, String name)
            throws IOException {
        Uri uri = DocumentsContract.createDocument(
                resolver,
                parent,
                MIME_DIR,
                name
        );
        if (uri == null) {
            throw new IOException(
                    "cannot create SAF directory:" + name
            );
        }
        return uri;
    }

    private Uri createFile(Uri parent, String name)
            throws IOException {
        Uri uri = DocumentsContract.createDocument(
                resolver,
                parent,
                MIME_FILE,
                name
        );
        if (uri == null) {
            throw new IOException(
                    "cannot create SAF document:" + name
            );
        }
        return uri;
    }

    private Uri findChild(
            Uri parent,
            String name,
            boolean directory
    ) throws IOException {
        for (Child child : listChildren(parent)) {
            if (name.equals(child.name)
                    && child.directory == directory) {
                return child.uri;
            }
        }
        return null;
    }

    private List<Child> listChildren(Uri parent)
            throws IOException {
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri children =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri,
                        parentId
                );
        List<Child> out = new ArrayList<>();
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = resolver.query(
                children,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                throw new IOException(
                        "SAF tree query unavailable"
                );
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String displayName = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.isNull(3)
                        ? 0
                        : Math.max(0, cursor.getLong(3));
                long modified = cursor.isNull(4)
                        ? 0
                        : Math.max(0, cursor.getLong(4));
                Uri documentUri =
                        DocumentsContract
                            .buildDocumentUriUsingTree(
                                    treeUri,
                                    documentId
                            );
                out.add(new Child(
                        displayName,
                        documentUri,
                        MIME_DIR.equals(mime),
                        size,
                        modified
                ));
            }
        } catch (RuntimeException error) {
            throw new IOException(
                    "SAF tree access failed",
                    error
            );
        }
        return out;
    }

    private void deleteIfExists(
            Uri parent,
            String name,
            boolean directory
    ) throws IOException {
        Uri uri = findChild(parent, name, directory);
        if (uri != null
                && !DocumentsContract.deleteDocument(
                        resolver,
                        uri
                )) {
            throw new IOException(
                    "cannot delete SAF document:" + name
            );
        }
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
                return Math.max(
                        0,
                        cursor.getLong(0)
                );
            }
        } catch (RuntimeException ignored) {
            // Descriptive metadata only.
        }
        return 0;
    }

    private long documentSizeRecursive(Uri uri, int depth)
            throws IOException {
        if (depth > 8) {
            throw new IOException(
                    "SAF revision nesting too deep"
            );
        }
        String[] projection = {
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        boolean directory = false;
        long ownSize = 0;
        try (Cursor cursor = resolver.query(
                uri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                directory = MIME_DIR.equals(
                        cursor.getString(0)
                );
                ownSize = cursor.isNull(1)
                        ? 0
                        : Math.max(0, cursor.getLong(1));
            }
        } catch (RuntimeException error) {
            throw new IOException(
                    "SAF size query failed",
                    error
            );
        }
        if (!directory) return ownSize;

        long total = 0;
        for (Child child : listChildren(uri)) {
            total += child.directory
                    ? documentSizeRecursive(
                            child.uri,
                            depth + 1
                    )
                    : child.size;
        }
        return total;
    }

    private String readText(Uri uri, int maxBytes)
            throws IOException {
        try (InputStream input =
                resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException(
                        "SAF input unavailable"
                );
            }
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(
                            "SAF document exceeds size budget"
                    );
                }
                output.write(buffer, 0, read);
            }
            return new String(
                    output.toByteArray(),
                    StandardCharsets.UTF_8
            );
        } catch (RuntimeException error) {
            throw new IOException(
                    "SAF read failed",
                    error
            );
        }
    }

    private void writeText(Uri uri, String value)
            throws IOException {
        byte[] bytes = value.getBytes(
                StandardCharsets.UTF_8
        );
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IOException(
                    "SAF project exceeds size budget"
            );
        }
        try (OutputStream output =
                resolver.openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IOException(
                        "SAF output unavailable"
                );
            }
            output.write(bytes);
            output.flush();
        } catch (RuntimeException error) {
            throw new IOException(
                    "SAF write failed",
                    error
            );
        }
    }

    private long journalLong(String payload, String key)
            throws IOException {
        for (String line : payload.split("\n")) {
            if (line.startsWith(key + "=")) {
                try {
                    return Long.parseLong(
                            line.substring(
                                    key.length() + 1
                            )
                    );
                } catch (RuntimeException error) {
                    throw new IOException(
                            "SAF journal field invalid",
                            error
                    );
                }
            }
        }
        throw new IOException(
                "SAF journal field missing:" + key
        );
    }

    private int integer(String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IOException(
                    "SAF project index integer invalid",
                    error
            );
        }
    }

    private Object storeLock() {
        return LOCKS.computeIfAbsent(
                parentDocumentUri.toString(),
                ignored -> new Object()
        );
    }

    private static Uri rootDocumentUri(Uri treeUri) {
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw new IllegalArgumentException(
                    "SAF tree URI required"
            );
        }
        String rootDocumentId =
                DocumentsContract.getTreeDocumentId(treeUri);
        return DocumentsContract
                .buildDocumentUriUsingTree(
                        treeUri,
                        rootDocumentId
                );
    }

    private static String revisionName(long revision) {
        return "revision-" + revision;
    }

    private static String legacyRevisionName(long revision) {
        return "revision-" + revision + ".tbx";
    }

    private static long valueOr(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static final class Child {
        final String name;
        final Uri uri;
        final boolean directory;
        final long size;
        final long modified;

        Child(
                String name,
                Uri uri,
                boolean directory,
                long size,
                long modified
        ) {
            this.name = name;
            this.uri = uri;
            this.directory = directory;
            this.size = size;
            this.modified = modified;
        }
    }
}
