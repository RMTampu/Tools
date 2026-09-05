package com.toolbox.tools.core;

import java.io.IOException;
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

    void ensureLayout() throws IOException;
    void write(Area area, String name, byte[] bytes) throws IOException;
    byte[] read(Area area, String name) throws IOException;
    boolean exists(Area area, String name) throws IOException;
    List<String> list(Area area) throws IOException;
}
