package com.toolbox.tools.core;

import com.toolbox.tools.authoring.DefaultAuthoringFactory;
import com.toolbox.tools.authoring.UnifiedAuthoringWorkspace;
import com.toolbox.tools.editor.DefaultEditorFactory;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.integration.ExternalIntegrationManager;
import com.toolbox.tools.library.AssetStore;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.FileAssetStore;
import com.toolbox.tools.library.InMemoryAssetStore;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.runtime.DefaultRuntimeFactory;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import com.toolbox.tools.repair.HealthMonitor;
import com.toolbox.tools.repair.RecoveryPreviewService;
import com.toolbox.tools.repair.RepairSessionManager;

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
    private final RuntimeEnvironment runtimeEnvironment;
    private final EditorEnvironment editorEnvironment;
    private final UnifiedAuthoringWorkspace authoringWorkspace;
    private final ExternalIntegrationManager externalIntegrationManager;
    private final RepairSessionManager repairSessionManager;
    private final RecoveryPreviewService recoveryPreviewService;
    private final HealthMonitor healthMonitor;
    private AppState state;

    public AppKernel(
            ToolRegistry toolRegistry,
            EngineManager engineManager,
            ConfigStore configStore,
            RecoveryManager recoveryManager,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore,
            RuntimeEnvironment runtimeEnvironment,
            EditorEnvironment editorEnvironment
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.engineManager = Objects.requireNonNull(engineManager, "engineManager");
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager");
        this.projectManager = Objects.requireNonNull(projectManager, "projectManager");
        this.libraryManager = Objects.requireNonNull(libraryManager, "libraryManager");
        this.assetStore = Objects.requireNonNull(assetStore, "assetStore");
        this.runtimeEnvironment = Objects.requireNonNull(
                runtimeEnvironment,
                "runtimeEnvironment"
        );
        this.editorEnvironment = Objects.requireNonNull(
                editorEnvironment,
                "editorEnvironment"
        );
        this.authoringWorkspace = DefaultAuthoringFactory.create(
                this.runtimeEnvironment,
                this.editorEnvironment,
                this.libraryManager
        );
        this.externalIntegrationManager = new ExternalIntegrationManager();
        this.repairSessionManager = new RepairSessionManager(
                this.projectManager,
                this.recoveryManager
        );
        this.recoveryPreviewService = new RecoveryPreviewService(
                this.projectManager
        );
        this.healthMonitor = new HealthMonitor();
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
        LibraryManager library = DefaultLibraryFactory.create();
        return create(
                recovery,
                projectManager,
                library,
                new InMemoryAssetStore(),
                DefaultRuntimeFactory.create(library.components()),
                DefaultEditorFactory.create()
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
        LibraryManager library = DefaultLibraryFactory.create();
        return create(
                recovery,
                projectManager,
                library,
                new FileAssetStore(assetLibraryRoot),
                DefaultRuntimeFactory.create(library.components()),
                DefaultEditorFactory.create()
        );
    }

    private static AppKernel create(
            RecoveryManager recovery,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore,
            RuntimeEnvironment runtimeEnvironment,
            EditorEnvironment editorEnvironment
    ) {
        AppKernel kernel = new AppKernel(
                new ToolRegistry(),
                new EngineManager(),
                new ConfigStore(),
                recovery,
                projectManager,
                libraryManager,
                assetStore,
                runtimeEnvironment,
                editorEnvironment
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
            configStore.put("tahap", "8");
            projectManager.bootstrap("project.default");
            state = AppState.READY;
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired();
            state = AppState.ERROR;
        }
    }

    public ToolRegistry toolRegistry() { return toolRegistry; }
    public EngineManager engineManager() { return engineManager; }
    public ConfigStore configStore() { return configStore; }
    public RecoveryManager recoveryManager() { return recoveryManager; }
    public ProjectManager projectManager() { return projectManager; }
    public LibraryManager libraryManager() { return libraryManager; }
    public AssetStore assetStore() { return assetStore; }
    public RuntimeEnvironment runtimeEnvironment() { return runtimeEnvironment; }
    public EditorEnvironment editorEnvironment() { return editorEnvironment; }
    public UnifiedAuthoringWorkspace authoringWorkspace() { return authoringWorkspace; }
    public ExternalIntegrationManager externalIntegrationManager() { return externalIntegrationManager; }
    public RepairSessionManager repairSessionManager() { return repairSessionManager; }
    public RecoveryPreviewService recoveryPreviewService() { return recoveryPreviewService; }
    public HealthMonitor healthMonitor() { return healthMonitor; }
    public synchronized AppState state() { return state; }
}
