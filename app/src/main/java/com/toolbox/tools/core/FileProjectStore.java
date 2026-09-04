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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class FileProjectStore implements ProjectStore {
    public static final int MAX_REVISIONS = 32;

    private final Path root;
    private final Path revisions;
    private final Path currentRef;
    private final Path previousRef;
    private final Path journal;
    private final Path lockFile;
    private final ProjectCodec codec = new ProjectCodec();
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

            long nextRevision = actual + 1;
            ProjectState committed = workingState.withRevision(nextRevision);
            String encodedProject = codec.encode(committed);
            ProjectManifest manifest = ProjectManifest.from(committed, encodedProject);

            writeAndSync(
                    journal,
                    ("expected=" + actual + "\nnext=" + nextRevision + "\n")
                            .getBytes(StandardCharsets.UTF_8)
            );

            Path revisionDir = revisions.resolve(Long.toString(nextRevision));
            if (Files.exists(revisionDir)) {
                throw new IOException("revision already exists");
            }
            Files.createDirectories(revisionDir);
            Path projectFile = revisionDir.resolve("project.json");
            Path manifestFile = revisionDir.resolve("project.manifest");
            Path indexFile = revisionDir.resolve("project.index");

            writeAndSync(projectFile, encodedProject.getBytes(StandardCharsets.UTF_8));
            writeAndSync(manifestFile, manifest.encode().getBytes(StandardCharsets.UTF_8));
            writeAndSync(indexFile, buildIndex(committed).getBytes(StandardCharsets.UTF_8));

            ProjectState reread = readRevision(revisionDir);
            if (!committed.equals(reread)) {
                throw new IOException("staged revision verification failed");
            }

            if (actual > 0) {
                replaceRef(previousRef, actual);
            }
            replaceRef(currentRef, nextRevision);
            Files.deleteIfExists(journal);
            trimOldRevisions();
            return committed;
        }
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
        List<Long> ids;
        try (java.util.stream.Stream<Path> stream = Files.list(revisions)) {
            ids = stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d+"))
                    .map(Long::parseLong)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }
        for (long id : ids) {
            if (id == current) {
                continue;
            }
            Path projectFile = revisions.resolve(Long.toString(id)).resolve("project.json");
            long size = Files.isRegularFile(projectFile) ? Files.size(projectFile) : 0;
            out.add(new RecoveryCandidate(
                    id == previous
                            ? RecoveryCandidate.Kind.LAST_VALID_REVISION
                            : RecoveryCandidate.Kind.OLDER_REVISION,
                    id,
                    size
            ));
        }
        return out;
    }

    private ProjectState readRevision(Path revisionDir) throws IOException {
        Path projectFile = revisionDir.resolve("project.json");
        Path manifestFile = revisionDir.resolve("project.manifest");
        if (!Files.isRegularFile(projectFile) || !Files.isRegularFile(manifestFile)) {
            throw new IOException("revision incomplete");
        }
        String encodedProject = new String(
                Files.readAllBytes(projectFile),
                StandardCharsets.UTF_8
        );
        String encodedManifest = new String(
                Files.readAllBytes(manifestFile),
                StandardCharsets.UTF_8
        );
        try {
            ProjectState state = codec.decode(encodedProject);
            ProjectManifest manifest = ProjectManifest.decode(encodedManifest);
            if (!manifest.verifies(state, encodedProject)) {
                throw new IOException("manifest verification failed");
            }
            ProjectValidationResult validation = validator.validate(state);
            if (!validation.isPass()
                    && state.schemaVersion() == ProjectState.CURRENT_SCHEMA_VERSION) {
                throw new IOException("stored project invalid:" + validation.message());
            }
            return state;
        } catch (IllegalArgumentException error) {
            throw new IOException("revision corrupt", error);
        }
    }

    private void recoverInterruptedTransaction() throws IOException {
        if (!Files.isRegularFile(journal)) {
            return;
        }
        String text = new String(Files.readAllBytes(journal), StandardCharsets.UTF_8);
        long next = parseJournal(text, "next");
        long current = Files.isRegularFile(currentRef) ? readRef(currentRef) : 0;
        if (current != next) {
            Path unfinished = revisions.resolve(Long.toString(next));
            if (Files.isDirectory(unfinished)) {
                deleteTree(unfinished);
            }
        }
        Files.deleteIfExists(journal);
    }

    private void trimOldRevisions() throws IOException {
        List<Long> ids;
        try (java.util.stream.Stream<Path> stream = Files.list(revisions)) {
            ids = stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d+"))
                    .map(Long::parseLong)
                    .sorted()
                    .collect(Collectors.toList());
        }
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

    private static String buildIndex(ProjectState state) {
        StringBuilder out = new StringBuilder();
        out.append("projectId=").append(state.projectId()).append('\n');
        out.append("revision=").append(state.revision()).append('\n');
        for (String id : state.resources().keySet()) {
            out.append("resource=").append(id).append('\n');
        }
        return out.toString();
    }

    private static void replaceRef(Path target, long revision) throws IOException {
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        writeAndSync(temp, (Long.toString(revision) + "\n").getBytes(StandardCharsets.UTF_8));
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
                    new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim()
            );
        } catch (RuntimeException error) {
            throw new IOException("revision ref invalid", error);
        }
    }

    private static long parseJournal(String text, String key) throws IOException {
        for (String line : text.split("\n")) {
            if (line.startsWith(key + "=")) {
                try {
                    return Long.parseLong(line.substring(key.length() + 1));
                } catch (RuntimeException error) {
                    throw new IOException("journal invalid", error);
                }
            }
        }
        throw new IOException("journal field missing");
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
