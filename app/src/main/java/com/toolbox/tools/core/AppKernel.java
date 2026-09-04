package com.toolbox.tools.core;

import com.toolbox.tools.library.AssetStore;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.FileAssetStore;
import com.toolbox.tools.library.InMemoryAssetStore;
import com.toolbox.tools.library.LibraryManager;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class AppKernel {
    private final ToolRegistry toolRegistry;
    private final EngineManager engineManager;
    private final ConfigStore configStore;
    private final RecoveryManager recoveryManager;
    private final ProjectManager projectManager;
    private final LibraryManager libraryManager;
    private final AssetStore assetStore;
    private AppState state;

    public AppKernel(
            ToolRegistry toolRegistry,
            EngineManager engineManager,
            ConfigStore configStore,
            RecoveryManager recoveryManager,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.engineManager = Objects.requireNonNull(engineManager, "engineManager");
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager");
        this.projectManager = Objects.requireNonNull(projectManager, "projectManager");
        this.libraryManager = Objects.requireNonNull(libraryManager, "libraryManager");
        this.assetStore = Objects.requireNonNull(assetStore, "assetStore");
        this.state = AppState.CREATED;
    }

    public static AppKernel createDefault() {
        RecoveryManager recovery = new RecoveryManager();
        ProjectManager projectManager = new ProjectManager(
                new InMemoryProjectStore(),
                new DraftRecoveryStore(),
                new RecoverySnapshotStore(),
                recovery,
                new ProjectMigrationRegistry()
        );
        return create(
                recovery,
                projectManager,
                DefaultLibraryFactory.create(),
                new InMemoryAssetStore()
        );
    }

    public static AppKernel createPersistent(File projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        File parent = projectRoot.getParentFile();
        File appFilesRoot = parent == null ? projectRoot : parent.getParentFile();
        if (appFilesRoot == null) appFilesRoot = projectRoot;
        return createPersistent(
                projectRoot,
                new File(appFilesRoot, "library/assets")
        );
    }

    public static AppKernel createPersistent(
            File projectRoot,
            File assetLibraryRoot
    ) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(assetLibraryRoot, "assetLibraryRoot");
        RecoveryManager recovery = new RecoveryManager();
        ProjectManager projectManager = new ProjectManager(
                new FileProjectStore(projectRoot),
                new DraftRecoveryStore(projectRoot),
                new RecoverySnapshotStore(projectRoot),
                recovery,
                new ProjectMigrationRegistry()
        );
        return create(
                recovery,
                projectManager,
                DefaultLibraryFactory.create(),
                new FileAssetStore(assetLibraryRoot)
        );
    }

    private static AppKernel create(
            RecoveryManager recovery,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore
    ) {
        AppKernel kernel = new AppKernel(
                new ToolRegistry(),
                new EngineManager(),
                new ConfigStore(),
                recovery,
                projectManager,
                libraryManager,
                assetStore
        );
        kernel.initialize();
        return kernel;
    }

    public synchronized void initialize() {
        state = AppState.INITIALIZING;
        try {
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
            configStore.put("tahap", "3");
            projectManager.bootstrap("project.default");
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

    public ProjectManager projectManager() {
        return projectManager;
    }

    public LibraryManager libraryManager() {
        return libraryManager;
    }

    public AssetStore assetStore() {
        return assetStore;
    }

    public synchronized AppState state() {
        return state;
    }
}
