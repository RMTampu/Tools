package com.toolbox.tools.core;

import java.io.IOException;

public final class InMemoryStorageGateway implements StorageGateway {
    private WorkspaceSnapshot snapshot;

    @Override
    public synchronized boolean exists() {
        return snapshot != null;
    }

    @Override
    public synchronized WorkspaceSnapshot load() throws IOException {
        if (snapshot == null) {
            throw new IOException("workspace does not exist");
        }
        return snapshot;
    }

    @Override
    public synchronized void save(WorkspaceSnapshot snapshot) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        this.snapshot = snapshot;
    }
}
