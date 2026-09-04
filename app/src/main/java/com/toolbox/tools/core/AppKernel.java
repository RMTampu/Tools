package com.toolbox.tools.core;

import java.io.IOException;

public final class AppKernel {
    private final ToolRegistry toolRegistry;
    private final EngineManager engineManager;
    private final ConfigStore configStore;
    private final RecoveryManager recoveryManager;
    private final WorkspaceManager workspaceManager;
    private AppState state;

    public AppKernel(
            ToolRegistry toolRegistry,
            EngineManager engineManager,
            ConfigStore configStore,
            RecoveryManager recoveryManager,
            WorkspaceManager workspaceManager
    ) {
        this.toolRegistry = toolRegistry;
        this.engineManager = engineManager;
        this.configStore = configStore;
        this.recoveryManager = recoveryManager;
        this.workspaceManager = workspaceManager;
        this.state = AppState.CREATED;
    }

    public static AppKernel createDefault() {
        RecoveryManager recoveryManager = new RecoveryManager();
        WorkspaceManager workspaceManager = new WorkspaceManager(
                new InMemoryStorageGateway(),
                recoveryManager
        );
        AppKernel kernel = new AppKernel(
                new ToolRegistry(),
                new EngineManager(),
                new ConfigStore(),
                recoveryManager,
                workspaceManager
        );
        kernel.initialize();
        return kernel;
    }

    public synchronized void initialize() {
        state = AppState.INITIALIZING;
        try {
            toolRegistry.register(new ToolDescriptor("foundation", "Foundation", "2.0"));
            engineManager.register(new EngineContract() {
                @Override
                public String id() {
                    return "foundation-engine";
                }

                @Override
                public boolean isReady() {
                    return true;
                }
            });
            configStore.put("targetApi", "30");
            configStore.put("targetAbi", "arm64");
            configStore.put("stage", "2");
            workspaceManager.bootstrap("toolbox.default");
            state = AppState.READY;
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired();
            state = AppState.ERROR;
        }
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    public EngineManager engineManager() {
        return engineManager;
    }

    public ConfigStore configStore() {
        return configStore;
    }

    public RecoveryManager recoveryManager() {
        return recoveryManager;
    }

    public WorkspaceManager workspaceManager() {
        return workspaceManager;
    }

    public synchronized AppState state() {
        return state;
    }
}
