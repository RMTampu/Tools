package com.toolbox.tools.core;

public final class AppKernel {
    private final ToolRegistry toolRegistry;
    private final EngineManager engineManager;
    private final ConfigStore configStore;
    private final RecoveryManager recoveryManager;
    private AppState state;

    public AppKernel(
            ToolRegistry toolRegistry,
            EngineManager engineManager,
            ConfigStore configStore,
            RecoveryManager recoveryManager
    ) {
        this.toolRegistry = toolRegistry;
        this.engineManager = engineManager;
        this.configStore = configStore;
        this.recoveryManager = recoveryManager;
        this.state = AppState.CREATED;
    }

    public static AppKernel createDefault() {
        AppKernel kernel = new AppKernel(
                new ToolRegistry(),
                new EngineManager(),
                new ConfigStore(),
                new RecoveryManager()
        );
        kernel.initialize();
        return kernel;
    }

    public synchronized void initialize() {
        state = AppState.INITIALIZING;
        toolRegistry.register(new ToolDescriptor("foundation", "Foundation", "1.0"));
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
        state = AppState.READY;
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

    public synchronized AppState state() {
        return state;
    }
}
