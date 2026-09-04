package com.toolbox.tools.core;

import java.io.IOException;

public interface StorageGateway {
    boolean exists() throws IOException;

    WorkspaceSnapshot load() throws IOException;

    void save(WorkspaceSnapshot snapshot) throws IOException;

    default boolean recoveredFromBackup() {
        return false;
    }
}
