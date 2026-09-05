package com.toolbox.tools.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface VisibleWorkspaceStore {
    enum Area {
        PROJECTS("Projects"),
        ASSETS("Assets"),
        TEMPLATES("Templates"),
        EXPORTS("Exports"),
        SNAPSHOTS("Snapshots"),
        BACKUPS("Backups");

        private final String folder;
        Area(String folder) { this.folder = folder; }
        public String folder() { return folder; }
    }

    final class WriteResult {
        private final long bytesWritten;
        private final String sha256;

        public WriteResult(long bytesWritten, String sha256) {
            if (bytesWritten < 0
                    || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("write result invalid");
            }
            this.bytesWritten = bytesWritten;
            this.sha256 = sha256;
        }

        public long bytesWritten() { return bytesWritten; }
        public String sha256() { return sha256; }
    }

    void ensureLayout() throws IOException;
    void write(Area area, String name, byte[] bytes) throws IOException;
    WriteResult writeStream(
            Area area,
            String name,
            InputStream input,
            long maxBytes
    ) throws IOException;
    byte[] read(Area area, String name) throws IOException;
    InputStream openInputStream(Area area, String name) throws IOException;
    boolean exists(Area area, String name) throws IOException;
    List<String> list(Area area) throws IOException;
}
