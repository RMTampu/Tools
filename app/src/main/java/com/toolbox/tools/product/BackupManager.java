package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BackupManager {
    public static final class BackupRecord {
        private final String id;
        private final long revision;
        private final long createdAt;
        private final String status;

        BackupRecord(String id, long revision, long createdAt, String status) {
            this.id = id;
            this.revision = revision;
            this.createdAt = createdAt;
            this.status = status;
        }

        public String id() { return id; }
        public long revision() { return revision; }
        public long createdAt() { return createdAt; }
        public String status() { return status; }
    }

    private final ProjectManager projects;
    private final List<BackupRecord> records = new ArrayList<>();

    public BackupManager(ProjectManager projects) {
        this.projects = Objects.requireNonNull(projects, "projects");
    }

    public synchronized BackupRecord createVerified() throws IOException {
        if (projects.hasUnsavedChanges() || projects.savedRevision() <= 0) {
            projects.save();
        }
        projects.captureFinalRecoverySnapshot();
        long revision = projects.savedRevision();
        BackupRecord record = new BackupRecord(
                "backup." + revision + "." + (records.size() + 1),
                revision,
                System.currentTimeMillis(),
                "BACKUP_VERIFIED"
        );
        records.add(record);
        while (records.size() > 8) records.remove(0);
        return record;
    }

    public synchronized ProjectState restore(BackupRecord record)
            throws IOException {
        if (record == null) throw new NullPointerException("record");
        return projects.restoreRevision(record.revision());
    }

    public synchronized List<BackupRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }
}
