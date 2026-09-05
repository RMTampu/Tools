package com.toolbox.tools.core;

import com.toolbox.tools.authoring.DefaultAuthoringFactory;
import com.toolbox.tools.authoring.UnifiedAuthoringWorkspace;
import com.toolbox.tools.build.ApplicationIrBuilder;
import com.toolbox.tools.build.BuildValidator;
import com.toolbox.tools.build.BuildHandoffManager;
import com.toolbox.tools.build.CandidateIdentityFactory;
import com.toolbox.tools.build.ReadyCoordinator;
import com.toolbox.tools.delivery.RemotePatchVerifier;
import com.toolbox.tools.delivery.RemoteTrustAnchor;
import com.toolbox.tools.delivery.SafePatchManager;
import com.toolbox.tools.engine.ProductEngineSuite;
import com.toolbox.tools.editor.DefaultEditorFactory;
import com.toolbox.tools.editor.EditorEnvironment;
import com.toolbox.tools.integration.ExternalIntegrationManager;
import com.toolbox.tools.library.AssetStore;
import com.toolbox.tools.library.BuiltinAssetCatalog;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.FileAssetStore;
import com.toolbox.tools.library.InMemoryAssetStore;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.live.CapabilityScanner;
import com.toolbox.tools.live.DefaultLiveFactory;
import com.toolbox.tools.live.LiveSessionManager;
import com.toolbox.tools.live.SelfEditPolicy;
import com.toolbox.tools.live.TargetDescriptor;
import com.toolbox.tools.runtime.DefaultRuntimeFactory;
import com.toolbox.tools.runtime.RuntimeEnvironment;
import com.toolbox.tools.repair.HealthMonitor;
import com.toolbox.tools.repair.RecoveryPreviewService;
import com.toolbox.tools.repair.RepairSessionManager;
import com.toolbox.tools.runtime.RuntimeModelValidator;
import com.toolbox.tools.product.DeclarativeProjectRuntime;
import com.toolbox.tools.product.EvolutionManager;
import com.toolbox.tools.product.ProductServices;
import com.toolbox.tools.product.SafeModeController;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class AppKernel {
    private final ToolRegistry toolRegistry;
    private final EngineManager engineManager;
    private final ConfigStore configStore;
    private final RecoveryManager recoveryManager;
    private final RuntimeStateStore runtimeStateStore;
    private final VisibleWorkspaceStore visibleWorkspaceStore;
    private final String bootstrapProjectId;
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
    private final CapabilityScanner capabilityScanner;
    private final TargetDescriptor selfTargetDescriptor;
    private final LiveSessionManager liveSessionManager;
    private final BuildValidator buildValidator;
    private final ApplicationIrBuilder applicationIrBuilder;
    private final CandidateIdentityFactory candidateIdentityFactory;
    private final ReadyCoordinator readyCoordinator;
    private final BuildHandoffManager buildHandoffManager;
    private final RemotePatchVerifier remotePatchVerifier;
    private final DeclarativeProjectRuntime declarativeRuntime;
    private final SafePatchManager safePatchManager;
    private final ProductServices productServices;
    private final EvolutionManager evolutionManager;
    private final SafeModeController safeModeController;
    private ProductEngineSuite productEngines;
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
        this(
                toolRegistry,
                engineManager,
                configStore,
                recoveryManager,
                recoveryManager.stateStore(),
                new MemoryVisibleWorkspaceStore(),
                projectManager,
                libraryManager,
                assetStore,
                runtimeEnvironment,
                editorEnvironment
        );
    }

    public AppKernel(
            ToolRegistry toolRegistry,
            EngineManager engineManager,
            ConfigStore configStore,
            RecoveryManager recoveryManager,
            RuntimeStateStore runtimeStateStore,
            VisibleWorkspaceStore visibleWorkspaceStore,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore,
            RuntimeEnvironment runtimeEnvironment,
            EditorEnvironment editorEnvironment
    ) {
        this(
                toolRegistry,
                engineManager,
                configStore,
                recoveryManager,
                runtimeStateStore,
                visibleWorkspaceStore,
                projectManager,
                libraryManager,
                assetStore,
                runtimeEnvironment,
                editorEnvironment,
                "project.default"
        );
    }

    public AppKernel(
            ToolRegistry toolRegistry,
            EngineManager engineManager,
            ConfigStore configStore,
            RecoveryManager recoveryManager,
            RuntimeStateStore runtimeStateStore,
            VisibleWorkspaceStore visibleWorkspaceStore,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore,
            RuntimeEnvironment runtimeEnvironment,
            EditorEnvironment editorEnvironment,
            String bootstrapProjectId
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.engineManager = Objects.requireNonNull(engineManager, "engineManager");
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager");
        this.runtimeStateStore = Objects.requireNonNull(
                runtimeStateStore,
                "runtimeStateStore"
        );
        this.visibleWorkspaceStore = Objects.requireNonNull(
                visibleWorkspaceStore,
                "visibleWorkspaceStore"
        );
        this.bootstrapProjectId = StableId.require(
                bootstrapProjectId,
                "bootstrapProjectId"
        );
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
                this.libraryManager,
                this.visibleWorkspaceStore
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
        this.capabilityScanner = new CapabilityScanner();
        this.selfTargetDescriptor = DefaultLiveFactory.selfTarget();
        this.liveSessionManager = new LiveSessionManager(
                this.projectManager,
                this.repairSessionManager,
                new SelfEditPolicy(),
                this.selfTargetDescriptor
        );
        this.buildValidator = new BuildValidator();
        this.applicationIrBuilder = new ApplicationIrBuilder();
        this.candidateIdentityFactory = new CandidateIdentityFactory();
        this.readyCoordinator = new ReadyCoordinator(
                this,
                this.buildValidator,
                this.applicationIrBuilder
        );
        this.buildHandoffManager = new BuildHandoffManager(
                this.projectManager,
                this.readyCoordinator,
                this.visibleWorkspaceStore,
                this.configStore
        );
        this.remotePatchVerifier = RemoteTrustAnchor.createVerifier();
        this.declarativeRuntime = new DeclarativeProjectRuntime(
                ProjectState.create(this.bootstrapProjectId)
        );
        this.safePatchManager = new SafePatchManager(
                this.projectManager,
                this.recoveryManager,
                this.remotePatchVerifier,
                this.declarativeRuntime::reload
        );
        this.productServices = new ProductServices(
                this.projectManager,
                this.runtimeStateStore,
                this.recoveryManager,
                this.visibleWorkspaceStore,
                this.runtimeEnvironment
        );
        this.evolutionManager = new EvolutionManager(
                this.safePatchManager,
                this.remotePatchVerifier
        );
        this.safeModeController = new SafeModeController(
                this.projectManager,
                this.recoveryManager,
                this.runtimeStateStore
        );
        this.safePatchManager.setHealthGate(
                this::patchHealthGate
        );
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
                recovery.stateStore(),
                new MemoryVisibleWorkspaceStore(),
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
        RuntimeStateStore runtimeState = new FileRuntimeStateStore(
                new File(projectRoot, "secure-runtime.properties")
        );
        RecoveryManager recovery = new RecoveryManager(runtimeState);
        VisibleWorkspaceStore visible = new FileVisibleWorkspaceStore(
                new File(projectRoot, "visible-fallback")
        );
        ProjectManager projectManager = new ProjectManager(
                new FileProjectStore(projectRoot),
                new DraftRecoveryStore(projectRoot),
                new RecoverySnapshotStore(visible),
                recovery,
                new ProjectMigrationRegistry()
        );
        LibraryManager library = DefaultLibraryFactory.create();
        return create(
                recovery,
                runtimeState,
                visible,
                projectManager,
                library,
                new FileAssetStore(assetLibraryRoot),
                DefaultRuntimeFactory.create(library.components()),
                DefaultEditorFactory.create()
        );
    }

    public static AppKernel createPersistent(
            ProjectStore projectStore,
            File privateProjectRoot,
            File assetLibraryRoot
    ) {
        Objects.requireNonNull(projectStore, "projectStore");
        Objects.requireNonNull(privateProjectRoot, "privateProjectRoot");
        Objects.requireNonNull(assetLibraryRoot, "assetLibraryRoot");

        return createPersistent(
                projectStore,
                privateProjectRoot,
                assetLibraryRoot,
                new FileVisibleWorkspaceStore(
                        new File(privateProjectRoot, "visible-fallback")
                )
        );
    }

    public static AppKernel createPersistent(
            ProjectStore projectStore,
            File privateProjectRoot,
            File assetLibraryRoot,
            VisibleWorkspaceStore visibleWorkspace
    ) {
        return createPersistent(
                projectStore,
                "project.default",
                privateProjectRoot,
                assetLibraryRoot,
                visibleWorkspace
        );
    }

    public static AppKernel createPersistent(
            ProjectStore projectStore,
            String bootstrapProjectId,
            File privateProjectRoot,
            File assetLibraryRoot,
            VisibleWorkspaceStore visibleWorkspace
    ) {
        Objects.requireNonNull(projectStore, "projectStore");
        StableId.require(bootstrapProjectId, "bootstrapProjectId");
        Objects.requireNonNull(privateProjectRoot, "privateProjectRoot");
        Objects.requireNonNull(assetLibraryRoot, "assetLibraryRoot");
        Objects.requireNonNull(visibleWorkspace, "visibleWorkspace");

        RuntimeStateStore runtimeState = new FileRuntimeStateStore(
                new File(privateProjectRoot, "secure-runtime.properties")
        );
        RecoveryManager recovery = new RecoveryManager(runtimeState);
        ProjectManager projectManager = new ProjectManager(
                projectStore,
                new DraftRecoveryStore(privateProjectRoot),
                new RecoverySnapshotStore(visibleWorkspace),
                recovery,
                new ProjectMigrationRegistry()
        );
        LibraryManager library = DefaultLibraryFactory.create();
        return create(
                recovery,
                runtimeState,
                visibleWorkspace,
                projectManager,
                library,
                new FileAssetStore(assetLibraryRoot),
                DefaultRuntimeFactory.create(library.components()),
                DefaultEditorFactory.create(),
                bootstrapProjectId
        );
    }

    private static AppKernel create(
            RecoveryManager recovery,
            RuntimeStateStore runtimeStateStore,
            VisibleWorkspaceStore visibleWorkspaceStore,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore,
            RuntimeEnvironment runtimeEnvironment,
            EditorEnvironment editorEnvironment
    ) {
        return create(
                recovery,
                runtimeStateStore,
                visibleWorkspaceStore,
                projectManager,
                libraryManager,
                assetStore,
                runtimeEnvironment,
                editorEnvironment,
                "project.default"
        );
    }

    private static AppKernel create(
            RecoveryManager recovery,
            RuntimeStateStore runtimeStateStore,
            VisibleWorkspaceStore visibleWorkspaceStore,
            ProjectManager projectManager,
            LibraryManager libraryManager,
            AssetStore assetStore,
            RuntimeEnvironment runtimeEnvironment,
            EditorEnvironment editorEnvironment,
            String bootstrapProjectId
    ) {
        AppKernel kernel = new AppKernel(
                new ToolRegistry(),
                new EngineManager(),
                new ConfigStore(),
                recovery,
                runtimeStateStore,
                visibleWorkspaceStore,
                projectManager,
                libraryManager,
                assetStore,
                runtimeEnvironment,
                editorEnvironment,
                bootstrapProjectId
        );
        kernel.initialize();
        return kernel;
    }

    public synchronized void initialize() {
        state = AppState.INITIALIZING;
        try {
            toolRegistry.register(new ToolDescriptor("foundation", "Fondasi", "12.0"));
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
            BuiltinAssetCatalog.install(
                    libraryManager.assets(),
                    assetStore
            );
            productEngines = ProductEngineSuite.register(
                    toolRegistry,
                    engineManager,
                    editorEnvironment,
                    runtimeEnvironment,
                    libraryManager,
                    assetStore
            );
            configStore.put("targetApi", "30");
            configStore.put("targetAbi", "arm64");
            configStore.put("tahap", "produk-penuh-v13-maksimal");
            configStore.put("bahasaDefault", "id");
            visibleWorkspaceStore.ensureLayout();
            projectManager.bootstrap(bootstrapProjectId);
            safePatchManager.bootstrap();
            productServices.freeze().bootstrap();
            declarativeRuntime.reload(projectManager.current());
            state = AppState.READY;
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired(
                    "KERNEL_INITIALIZATION_FAILED",
                    "BOOTSTRAP"
            );
            state = AppState.ERROR;
        }
    }

    private boolean patchHealthGate(ProjectState projectState) {
        if (projectState == null
                || !new ProjectValidator()
                    .validate(projectState)
                    .isPass()
                || !productServices.isReady()
                || !new RuntimeModelValidator()
                    .validate(runtimeEnvironment)
                    .isEmpty()
                || !runtimeEnvironment
                    .navigation()
                    .validateRoutes()
                    .isEmpty()
                || productEngines == null
                || !productEngines.semuaSiap()
                || libraryManager.components().allReady().isEmpty()
                || libraryManager.assets().allReady().isEmpty()
                || libraryManager.templates().allReady().isEmpty()
                || !productServices.inventory().complete()
                || !productServices.resources().invariantPass()) {
            return false;
        }

        try {
            visibleWorkspaceStore.ensureLayout();
            for (VisibleWorkspaceStore.Area area
                    : VisibleWorkspaceStore.Area.values()) {
                visibleWorkspaceStore.list(area);
            }
            for (java.util.Map.Entry<String, String> entry
                    : projectState.resources().entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("asset.external.")
                        || !key.endsWith(".storage.name")) {
                    continue;
                }
                String assetId = key.substring(
                        0,
                        key.length() - ".storage.name".length()
                );
                String area = projectState.resources().get(
                        assetId + ".storage.area"
                );
                String sha = projectState.resources().get(
                        assetId + ".sha256"
                );
                if (!VisibleWorkspaceStore.Area.ASSETS.folder()
                        .equals(area)
                        || sha == null
                        || !sha.matches("[0-9a-f]{64}")
                        || !productServices.assetIntegrity().verify(
                            visibleWorkspaceStore,
                            VisibleWorkspaceStore.Area.ASSETS,
                            entry.getValue(),
                            sha
                        )) {
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    public ToolRegistry toolRegistry() { return toolRegistry; }
    public EngineManager engineManager() { return engineManager; }
    public ConfigStore configStore() { return configStore; }
    public RecoveryManager recoveryManager() { return recoveryManager; }
    public RuntimeStateStore runtimeStateStore() { return runtimeStateStore; }
    public VisibleWorkspaceStore visibleWorkspaceStore() {
        return visibleWorkspaceStore;
    }
    public String bootstrapProjectId() { return bootstrapProjectId; }
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
    public CapabilityScanner capabilityScanner() { return capabilityScanner; }
    public TargetDescriptor selfTargetDescriptor() { return selfTargetDescriptor; }
    public LiveSessionManager liveSessionManager() { return liveSessionManager; }
    public BuildValidator buildValidator() { return buildValidator; }
    public ApplicationIrBuilder applicationIrBuilder() { return applicationIrBuilder; }
    public CandidateIdentityFactory candidateIdentityFactory() { return candidateIdentityFactory; }
    public ReadyCoordinator readyCoordinator() { return readyCoordinator; }
    public BuildHandoffManager buildHandoffManager() {
        return buildHandoffManager;
    }
    public RemotePatchVerifier remotePatchVerifier() { return remotePatchVerifier; }
    public SafePatchManager safePatchManager() { return safePatchManager; }
    public DeclarativeProjectRuntime declarativeRuntime() { return declarativeRuntime; }
    public ProductServices productServices() { return productServices; }
    public ProductEngineSuite productEngines() { return productEngines; }
    public EvolutionManager evolutionManager() { return evolutionManager; }
    public SafeModeController safeModeController() { return safeModeController; }
    public synchronized AppState state() { return state; }
}
