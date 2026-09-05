package com.toolbox.tools.product;

import com.toolbox.tools.core.MemoryVisibleWorkspaceStore;
import com.toolbox.tools.core.ProjectCodec;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.VisibleWorkspaceStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BackupManager {
    public static final class BackupRecord {
        private final String id;
        private final String fileName;
        private final long revision;
        private final long createdAt;
        private final String status;

        BackupRecord(
                String id,
                String fileName,
                long revision,
                long createdAt,
                String status
        ) {
            this.id = id;
            this.fileName = fileName;
            this.revision = revision;
            this.createdAt = createdAt;
            this.status = status;
        }

        public String id() { return id; }
        public String fileName() { return fileName; }
        public long revision() { return revision; }
        public long createdAt() { return createdAt; }
        public String status() { return status; }
    }

    private final ProjectManager projects;
    private final VisibleWorkspaceStore visible;
    private final ProjectCodec codec = new ProjectCodec();

    public BackupManager(ProjectManager projects) {
        this(projects, new MemoryVisibleWorkspaceStore());
    }

    public BackupManager(
            ProjectManager projects,
            VisibleWorkspaceStore visible
    ) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.visible = Objects.requireNonNull(visible, "visible");
    }

    public synchronized BackupRecord createVerified() throws IOException {
        if (projects.hasUnsavedChanges() || projects.savedRevision() <= 0) {
            projects.save();
        }
        projects.captureFinalRecoverySnapshot();

        ProjectState source = projects.current();
        long revision = projects.savedRevision();
        long createdAt = System.currentTimeMillis();
        String fileName = "backup-r"
                + revision
                + "-"
                + createdAt
                + ".tbx";
        byte[] payload = codec.encode(source).getBytes(StandardCharsets.UTF_8);
        visible.write(
                VisibleWorkspaceStore.Area.BACKUPS,
                fileName,
                payload
        );

        ProjectState verified = codec.decode(new String(
                visible.read(
                        VisibleWorkspaceStore.Area.BACKUPS,
                        fileName
                ),
                StandardCharsets.UTF_8
        ));
        if (!source.projectId().equals(verified.projectId())
                || source.revision() != verified.revision()) {
            throw new IOException("backup verification mismatch");
        }

        return new BackupRecord(
                "backup." + revision + "." + createdAt,
                fileName,
                revision,
                createdAt,
                "BACKUP_VERIFIED"
        );
    }

    public synchronized ProjectState restore(BackupRecord record)
            throws IOException {
        if (record == null) throw new NullPointerException("record");
        byte[] payload = visible.read(
                VisibleWorkspaceStore.Area.BACKUPS,
                record.fileName()
        );
        ProjectState candidate = codec.decode(
                new String(payload, StandardCharsets.UTF_8)
        );
        if (candidate.revision() != record.revision()) {
            throw new IOException("backup revision mismatch");
        }
        return projects.restoreExternalState(candidate);
    }

    public synchronized List<BackupRecord> records() {
        try {
            List<BackupRecord> out = new ArrayList<>();
            for (String name : visible.list(
                    VisibleWorkspaceStore.Area.BACKUPS
            )) {
                BackupRecord parsed = parse(name);
                if (parsed != null) out.add(parsed);
            }
            out.sort(
                    Comparator.comparingLong(
                            BackupRecord::createdAt
                    ).reversed()
            );
            return Collections.unmodifiableList(out);
        } catch (IOException error) {
            return Collections.emptyList();
        }
    }

    private static BackupRecord parse(String name) {
        if (name == null || !name.matches(
                "backup-r[0-9]+-[0-9]+\\.tbx"
        )) {
            return null;
        }
        try {
            String body = name.substring(
                    "backup-r".length(),
                    name.length() - ".tbx".length()
            );
            int split = body.indexOf('-');
            long revision = Long.parseLong(body.substring(0, split));
            long createdAt = Long.parseLong(body.substring(split + 1));
            return new BackupRecord(
                    "backup." + revision + "." + createdAt,
                    name,
                    revision,
                    createdAt,
                    "BACKUP_VERIFIED"
            );
        } catch (RuntimeException error) {
            return null;
        }
    }
}
