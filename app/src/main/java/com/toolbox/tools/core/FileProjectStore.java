package com.toolbox.tools.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FileProjectStore implements ProjectStore {
    public static final int MAX_REVISIONS = 32;

    private final Path root;
    private final Path revisions;
    private final Path currentRef;
    private final Path previousRef;
    private final Path journal;
    private final Path lockFile;
    private final ProjectDefinitionCodec definitionCodec = new ProjectDefinitionCodec();
    private final ProjectValidator validator = new ProjectValidator();

    public FileProjectStore(File root) {
        this.root = root.toPath();
        this.revisions = this.root.resolve("revisions");
        this.currentRef = this.root.resolve("current.ref");
        this.previousRef = this.root.resolve("previous.ref");
        this.journal = this.root.resolve("journal.pending");
        this.lockFile = this.root.resolve("commit.lock");
    }

    @Override
    public synchronized ProjectLoadResult load(String projectId) throws IOException {
        StableId.require(projectId, "projectId");
        Files.createDirectories(revisions);
        recoverInterruptedTransaction();

        if (!Files.isRegularFile(currentRef)) {
            return new ProjectLoadResult(
                    ProjectAccessStatus.FOLDER_MISSING,
                    null,
                    new ArrayList<>()
            );
        }

        long current = readRef(currentRef);
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
            if (Files.isRegularFile(previousRef)) {
                try {
                    fallback = loadRevision(readRef(previousRef));
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
            throw new IOException("PROJECT_VALIDATION_FAILED:" + validation.message());
        }

        Files.createDirectories(root);
        Files.createDirectories(revisions);

        try (FileChannel channel = FileChannel.open(
                lockFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {

            long actual = Files.isRegularFile(currentRef) ? readRef(currentRef) : 0;
            if (actual != expectedRevision) {
                throw new StaleWriteException(expectedRevision, actual);
            }

            ProjectState committed = publishRevision(
                    workingState,
                    actual + 1,
                    actual > 0 ? actual : -1
            );
            trimOldRevisions();
            return committed;
        }
    }

    @Override
    public synchronized ProjectState recoverRevision(long revision) throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(revisions);

        try (FileChannel channel = FileChannel.open(
                lockFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {

            long actual = Files.isRegularFile(currentRef) ? readRef(currentRef) : 0;
            ProjectState candidate = loadRevision(revision);
            ProjectValidationResult validation = validator.validate(candidate);
            if (!validation.isPass()) {
                throw new IOException(
                        "RECOVERY_CANDIDATE_INVALID:" + validation.message()
                );
            }
            ProjectState recovered = publishRevision(
                    candidate,
                    actual + 1,
                    revision
            );
            trimOldRevisions();
            return recovered;
        }
    }

    private ProjectState publishRevision(
            ProjectState source,
            long nextRevision,
            long previousValidRevision
    ) throws IOException {
        ProjectState committed = source.withRevision(nextRevision);
        String encodedDefinition = definitionCodec.encode(committed);
        ProjectManifest manifest = ProjectManifest.from(
                committed,
                encodedDefinition
        );

        writeAndSync(
                journal,
                ("next=" + nextRevision + "\npreviousValid=" + previousValidRevision + "\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        Path revisionDir = revisions.resolve(Long.toString(nextRevision));
        if (Files.exists(revisionDir)) {
            throw new IOException("revision already exists");
        }

        Files.createDirectories(revisionDir);
        Path resourcesDir = revisionDir.resolve("resources");
        Files.createDirectories(resourcesDir);

        Path projectFile = revisionDir.resolve("project.json");
        Path manifestFile = revisionDir.resolve("project.manifest");
        Path indexFile = revisionDir.resolve("project.index");

        writeAndSync(projectFile, encodedDefinition.getBytes(StandardCharsets.UTF_8));

        for (Map.Entry<String, String> entry : committed.resources().entrySet()) {
            Path resourceFile = resourcesDir.resolve(
                    ProjectDefinitionCodec.resourceFileName(entry.getKey())
            );
            ensureChild(resourcesDir, resourceFile);
            writeAndSync(
                    resourceFile,
                    entry.getValue().getBytes(StandardCharsets.UTF_8)
            );
        }

        writeAndSync(
                manifestFile,
                manifest.encode().getBytes(StandardCharsets.UTF_8)
        );
        writeAndSync(
                indexFile,
                buildIndex(committed).getBytes(StandardCharsets.UTF_8)
        );

        ProjectState reread = readRevision(revisionDir);
        if (!committed.equals(reread)) {
            throw new IOException("staged revision verification failed");
        }

        if (previousValidRevision > 0) {
            replaceRef(previousRef, previousValidRevision);
        }
        replaceRef(currentRef, nextRevision);
        Files.deleteIfExists(journal);
        return committed;
    }

    @Override
    public synchronized ProjectState loadRevision(long revision) throws IOException {
        if (revision <= 0) {
            throw new IOException("revision invalid");
        }
        return readRevision(revisions.resolve(Long.toString(revision)));
    }

    @Override
    public synchronized List<RecoveryCandidate> recoveryCandidates() throws IOException {
        List<RecoveryCandidate> out = new ArrayList<>();
        if (!Files.isDirectory(revisions)) {
            return out;
        }

        long current = Files.isRegularFile(currentRef) ? readRef(currentRef) : -1;
        long previous = Files.isRegularFile(previousRef) ? readRef(previousRef) : -1;
        List<Long> ids = listRevisionIds(Comparator.reverseOrder());

        for (long id : ids) {
            if (id == current) {
                continue;
            }
            Path revisionDir = revisions.resolve(Long.toString(id));
            out.add(new RecoveryCandidate(
                    id == previous
                            ? RecoveryCandidate.Kind.LAST_VALID_REVISION
                            : RecoveryCandidate.Kind.OLDER_REVISION,
                    id,
                    directorySize(revisionDir)
            ));
        }
        return out;
    }

    private ProjectState readRevision(Path revisionDir) throws IOException {
        Path projectFile = revisionDir.resolve("project.json");
        Path manifestFile = revisionDir.resolve("project.manifest");
        Path indexFile = revisionDir.resolve("project.index");
        Path resourcesDir = revisionDir.resolve("resources");

        if (!Files.isRegularFile(projectFile)
                || !Files.isRegularFile(manifestFile)
                || !Files.isRegularFile(indexFile)
                || !Files.isDirectory(resourcesDir)) {
            throw new IOException("revision incomplete");
        }

        String encodedDefinition = new String(
                Files.readAllBytes(projectFile),
                StandardCharsets.UTF_8
        );
        String encodedManifest = new String(
                Files.readAllBytes(manifestFile),
                StandardCharsets.UTF_8
        );

        try {
            Map<String, String> resources = readIndexedResources(
                    indexFile,
                    resourcesDir
            );
            ProjectState state = definitionCodec.decode(
                    encodedDefinition,
                    resources
            );
            ProjectManifest manifest = ProjectManifest.decode(encodedManifest);
            if (!manifest.verifies(state, encodedDefinition)) {
                throw new IOException("manifest verification failed");
            }

            ProjectValidationResult validation = validator.validate(state);
            if (!validation.isPass()
                    && state.schemaVersion() == ProjectState.CURRENT_SCHEMA_VERSION) {
                throw new IOException(
                        "stored project invalid:" + validation.message()
                );
            }
            return state;
        } catch (IllegalArgumentException error) {
            throw new IOException("revision corrupt", error);
        }
    }

    private Map<String, String> readIndexedResources(
            Path indexFile,
            Path resourcesDir
    ) throws IOException {
        List<String> lines = Files.readAllLines(
                indexFile,
                StandardCharsets.UTF_8
        );
        Map<String, String> resources = new LinkedHashMap<>();
        int declaredCount = -1;

        for (String line : lines) {
            if (line.startsWith("resourceCount=")) {
                declaredCount = parseInt(line.substring("resourceCount=".length()));
            } else if (line.startsWith("resource=")) {
                String value = line.substring("resource=".length());
                int separator = value.indexOf('|');
                if (separator <= 0 || separator == value.length() - 1) {
                    throw new IOException("project index resource record invalid");
                }
                String id = value.substring(0, separator);
                StableId.require(id, "resourceId");
                String fileName = value.substring(separator + 1);
                if (!fileName.equals(ProjectDefinitionCodec.resourceFileName(id))) {
                    throw new IOException("project index filename mismatch");
                }
                Path resourceFile = resourcesDir.resolve(fileName);
                ensureChild(resourcesDir, resourceFile);
                if (!Files.isRegularFile(resourceFile)) {
                    throw new IOException("indexed resource missing");
                }
                byte[] bytes = Files.readAllBytes(resourceFile);
                if (bytes.length > ProjectState.MAX_RESOURCE_BYTES) {
                    throw new IOException("resource exceeds size budget");
                }
                if (resources.put(
                        id,
                        new String(bytes, StandardCharsets.UTF_8)
                ) != null) {
                    throw new IOException("duplicate indexed resource");
                }
            }
        }

        if (declaredCount < 0 || declaredCount != resources.size()) {
            throw new IOException("project index count mismatch");
        }

        try (java.util.stream.Stream<Path> stream = Files.list(resourcesDir)) {
            long physicalCount = stream.filter(Files::isRegularFile).count();
            if (physicalCount != resources.size()) {
                throw new IOException("unexpected resource payload");
            }
        }
        return resources;
    }

    private void recoverInterruptedTransaction() throws IOException {
        if (!Files.isRegularFile(journal)) {
            return;
        }
        String text = new String(
                Files.readAllBytes(journal),
                StandardCharsets.UTF_8
        );
        long next = parseJournal(text, "next");
        long current = Files.isRegularFile(currentRef) ? readRef(currentRef) : 0;

        if (current != next) {
            deleteTree(revisions.resolve(Long.toString(next)));
        }
        Files.deleteIfExists(journal);
    }

    private void trimOldRevisions() throws IOException {
        List<Long> ids = listRevisionIds(Comparator.naturalOrder());
        long current = Files.isRegularFile(currentRef) ? readRef(currentRef) : -1;
        long previous = Files.isRegularFile(previousRef) ? readRef(previousRef) : -1;

        while (ids.size() > MAX_REVISIONS) {
            long candidate = ids.remove(0);
            if (candidate == current || candidate == previous) {
                continue;
            }
            deleteTree(revisions.resolve(Long.toString(candidate)));
        }
    }

    private List<Long> listRevisionIds(Comparator<Long> comparator)
            throws IOException {
        if (!Files.isDirectory(revisions)) {
            return new ArrayList<>();
        }
        try (java.util.stream.Stream<Path> stream = Files.list(revisions)) {
            return stream.filter(Files::isDirectory)
                    .map(item -> item.getFileName().toString())
                    .filter(name -> name.matches("\\d+"))
                    .map(Long::parseLong)
                    .sorted(comparator)
                    .collect(Collectors.toList());
        }
    }

    private static String buildIndex(ProjectState state) {
        StringBuilder out = new StringBuilder();
        out.append("TBX_PROJECT_INDEX_V1\n");
        out.append("projectId=").append(state.projectId()).append('\n');
        out.append("revision=").append(state.revision()).append('\n');
        out.append("resourceCount=").append(state.resources().size()).append('\n');
        for (String id : state.resources().keySet()) {
            out.append("resource=")
                    .append(id)
                    .append('|')
                    .append(ProjectDefinitionCodec.resourceFileName(id))
                    .append('\n');
        }
        return out.toString();
    }

    private static void replaceRef(Path target, long revision) throws IOException {
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        writeAndSync(
                temp,
                (Long.toString(revision) + "\n").getBytes(StandardCharsets.UTF_8)
        );
        try {
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            throw new IOException("atomic ref publish unavailable", error);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static long readRef(Path path) throws IOException {
        try {
            return Long.parseLong(
                    new String(
                            Files.readAllBytes(path),
                            StandardCharsets.UTF_8
                    ).trim()
            );
        } catch (RuntimeException error) {
            throw new IOException("revision ref invalid", error);
        }
    }

    private static long parseJournal(String text, String key) throws IOException {
        for (String line : text.split("\n")) {
            if (line.startsWith(key + "=")) {
                try {
                    return Long.parseLong(
                            line.substring(key.length() + 1)
                    );
                } catch (RuntimeException error) {
                    throw new IOException("journal invalid", error);
                }
            }
        }
        throw new IOException("journal field missing");
    }

    private static int parseInt(String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException error) {
            throw new IOException("project index integer invalid", error);
        }
    }

    private static long directorySize(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            long total = 0;
            for (Path path : stream.collect(Collectors.toList())) {
                if (Files.isRegularFile(path)) {
                    total += Files.size(path);
                }
            }
            return total;
        }
    }

    private static void ensureChild(Path parent, Path child) throws IOException {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedParent)) {
            throw new IOException("resource path escaped project boundary");
        }
    }

    private static void writeAndSync(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (FileOutputStream stream = new FileOutputStream(path.toFile(), false)) {
            stream.write(bytes);
            stream.flush();
            stream.getFD().sync();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
