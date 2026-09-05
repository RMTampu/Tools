package com.toolbox.tools.product;

import com.toolbox.tools.protocol.ManagedAppProtocol;

import com.toolbox.tools.core.MemoryVisibleWorkspaceStore;
import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.RecoveryManager;
import com.toolbox.tools.core.RuntimeStateStore;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import java.util.Objects;

public final class ProductServices {
    private final ScreenManager screens;
    private final LocalizationManager localization;
    private final ThemeTokenManager themes;
    private final PermissionManager permissions;
    private final ResourceGuard resources;
    private final CacheManager cache;
    private final BackupManager backups;
    private final FreezeEngine freeze;
    private final BackgroundTaskManager backgroundTasks;
    private final ImportSecurityValidator importSecurity;
    private final DiagnosticCenter diagnostics;
    private final ClipboardService clipboard;
    private final ProjectGraphManager projectGraph;
    private final VisualLayoutEngine visualLayout;
    private final StateVariantEngine stateVariants;
    private final AnimationEngine animations;
    private final PreviewSandbox previewSandbox;
    private final EditorContextStore editorContext;
    private final AppLifecycleManager lifecycle;
    private final ImportMergeManager importMerge;
    private final AutoRepairEngine autoRepair;
    private final ScaleBenchmarkHarness benchmark;
    private final ToolLifecycleManager toolLifecycle;
    private final ProductCompletionServices completion;
    private final ProductDeepContracts deep;
    private final RepositoryInventory inventory;
    private final InputRouter inputRouter;
    private final ConditionalPropertyEngine conditionalProperties;
    private final DataProviderRegistry dataProviders;
    private final AssetLoadManager assetLoads;
    private final RenderDiagnostics renderDiagnostics;
    private final ManagedAppProtocol managedAppProtocol;
    private final VisibleArtifactManager visibleArtifacts;

    public ProductServices(ProjectManager projects) {
        this(
                projects,
                projects.recoveryManager().stateStore(),
                projects.recoveryManager(),
                new MemoryVisibleWorkspaceStore()
        );
    }

    public ProductServices(
            ProjectManager projects,
            RuntimeStateStore runtimeState,
            RecoveryManager recovery,
            VisibleWorkspaceStore visibleWorkspace
    ) {
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(runtimeState, "runtimeState");
        Objects.requireNonNull(recovery, "recovery");
        Objects.requireNonNull(visibleWorkspace, "visibleWorkspace");
        screens = new ScreenManager();
        localization = new LocalizationManager();
        themes = new ThemeTokenManager();
        permissions = new PermissionManager();
        resources = new ResourceGuard();
        cache = new CacheManager();
        visibleArtifacts = new VisibleArtifactManager(
                projects,
                visibleWorkspace
        );
        backups = new BackupManager(projects, visibleWorkspace);
        freeze = new FreezeEngine(
                projects,
                runtimeState,
                recovery,
                visibleArtifacts
        );
        backgroundTasks = new BackgroundTaskManager();
        backgroundTasks.register(
                new BackgroundTaskManager.TaskSpec(
                        "task.project.index.refresh",
                        BackgroundTaskManager.DataType.NONE,
                        BackgroundTaskManager.DataType.BOOLEAN,
                        2,
                        30_000,
                        true,
                        java.util.EnumSet.of(
                                BackgroundTaskManager.Constraint.STORAGE_NOT_LOW
                        ),
                        BackgroundTaskManager.ExecutionClass.IO
                )
        );
        importSecurity = new ImportSecurityValidator();
        diagnostics = new DiagnosticCenter();
        clipboard = new ClipboardService();
        projectGraph = new ProjectGraphManager();
        visualLayout = new VisualLayoutEngine();
        stateVariants = new StateVariantEngine();
        animations = new AnimationEngine();
        previewSandbox = new PreviewSandbox();
        previewSandbox.putScenario(
                "preview.sample",
                PreviewSandbox.DataState.SAMPLE,
                java.util.Collections.singletonMap(
                        "title",
                        "Contoh"
                ),
                java.util.Collections.emptyList(),
                ""
        );
        previewSandbox.putScenario(
                "preview.loading",
                PreviewSandbox.DataState.LOADING,
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyList(),
                "Memuat"
        );
        previewSandbox.putScenario(
                "preview.error",
                PreviewSandbox.DataState.ERROR,
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyList(),
                "Contoh kegagalan"
        );
        previewSandbox.putScenario(
                "preview.empty",
                PreviewSandbox.DataState.EMPTY,
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyList(),
                "Tidak ada data"
        );
        java.util.Map<String,String> previewRow =
                new java.util.LinkedHashMap<>();
        previewRow.put("id", "row.1");
        previewRow.put("label", "Baris Contoh");
        previewSandbox.putScenario(
                "preview.list",
                PreviewSandbox.DataState.LIST,
                java.util.Collections.emptyMap(),
                java.util.Collections.singletonList(previewRow),
                ""
        );
        previewSandbox.simulateAction(
                "preview.action.result",
                PreviewSandbox.SideEffect.NETWORK,
                "Respons simulasi"
        );
        editorContext = new EditorContextStore(runtimeState);
        lifecycle = new AppLifecycleManager();
        lifecycle.registerAction(
                "lifecycle.home.every",
                "screen.home",
                AppLifecycleManager.Event.SCREEN_ENTER,
                AppLifecycleManager.Policy.EVERY_ENTER,
                0
        );
        lifecycle.registerAction(
                "lifecycle.home.first",
                "screen.home",
                AppLifecycleManager.Event.SCREEN_ENTER,
                AppLifecycleManager.Policy.FIRST_ENTER,
                0
        );
        lifecycle.registerAction(
                "lifecycle.home.stale",
                "screen.home",
                AppLifecycleManager.Event.SCREEN_RETURN,
                AppLifecycleManager.Policy.WHEN_DATA_STALE,
                60_000
        );
        importMerge = new ImportMergeManager();
        autoRepair = new AutoRepairEngine(
                projects,
                projectGraph,
                cache
        );
        benchmark = new ScaleBenchmarkHarness();
        toolLifecycle = new ToolLifecycleManager();
        completion = new ProductCompletionServices();
        deep = new ProductDeepContracts();
        inventory = new RepositoryInventory();
        inputRouter = new InputRouter();
        conditionalProperties = new ConditionalPropertyEngine();
        dataProviders = new DataProviderRegistry();
        assetLoads = new AssetLoadManager();
        renderDiagnostics = new RenderDiagnostics();
        managedAppProtocol = new ManagedAppProtocol();
        for (com.toolbox.tools.library.BuiltinAssetCatalog.BuiltinAsset item
                : com.toolbox.tools.library.BuiltinAssetCatalog.all()) {
            assetLoads.register(
                    item.descriptor().assetId(),
                    AssetLoadManager.Kind.JSON,
                    item.payload().length,
                    item.descriptor().sha256()
            );
            assetLoads.reference(item.descriptor().assetId());
        }

        projectGraph.registerEntity("screen.home");
        projectGraph.registerEntity("screen.detail");
        projectGraph.registerEntity("object.home.primary");
        projectGraph.link("object.home.primary", "screen.detail");

        inputRouter.register("screen.home", null);
        inputRouter.register("container.home.main", "screen.home");
        inputRouter.register("object.home.primary", "container.home.main");
        inputRouter.setFocusOrder(java.util.Arrays.asList("object.home.primary"));

        visualLayout.add(new VisualLayoutEngine.Node(
                "layout.root",
                null,
                0, 0, 360, 640, 0, false,
                VisualLayoutEngine.PointerBehavior.AUTO
        ));
        visualLayout.add(new VisualLayoutEngine.Node(
                "object.home.primary",
                "layout.root",
                24, 180, 148, 46, 1, false,
                VisualLayoutEngine.PointerBehavior.AUTO
        ));

        stateVariants.setNormal(
                "object.home.primary",
                "property.color",
                "#00F0B5"
        );
        stateVariants.setStateOverride(
                "object.home.primary",
                "state.pressed",
                "property.color",
                "#4CC9FF"
        );
        stateVariants.setLayerOverride(
                "object.home.primary",
                StateVariantEngine.Layer.ORIENTATION,
                "orientation.landscape",
                "property.width",
                "196"
        );
        stateVariants.setLayerOverride(
                "object.home.primary",
                StateVariantEngine.Layer.THEME,
                "theme.dark.neon",
                "property.color",
                "#00F0B5"
        );

        animations.register(new AnimationEngine.Animation(
                "animation.button.press",
                AnimationEngine.Kind.SCALE,
                "event.click",
                160,
                0,
                AnimationEngine.Easing.EASE_OUT
        ));
        animations.register(new AnimationEngine.Animation(
                "animation.button.fade",
                AnimationEngine.Kind.FADE,
                "event.enter",
                120,
                0,
                AnimationEngine.Easing.EASE_IN_OUT
        ));
        animations.registerGroup(new AnimationEngine.Group(
                "animation.group.home",
                AnimationEngine.GroupMode.SEQUENCE,
                java.util.Arrays.asList(
                        "animation.button.fade",
                        "animation.button.press"
                )
        ));
        visualLayout.addGuide(new VisualLayoutEngine.Guide(
                "guide.home.left",
                VisualLayoutEngine.GuideAxis.X,
                24
        ));
        visualLayout.addGuide(new VisualLayoutEngine.Guide(
                "guide.home.top",
                VisualLayoutEngine.GuideAxis.Y,
                180
        ));
        visualLayout.setResponsiveOverride(
                "screen.home",
                VisualLayoutEngine.Orientation.LANDSCAPE,
                "property.width",
                196
        );
        previewSandbox.putMock("mock.user.name", "Pengguna");
        lifecycle.emit(AppLifecycleManager.Event.APP_START, null);

        for (String tool : new String[]{
                "tool.ui","tool.logic","tool.data","tool.binding","tool.asset"
        }) {
            toolLifecycle.register(tool);
        }
        toolLifecycle.activate("tool.ui");
    }

    public ScreenManager screens() { return screens; }
    public LocalizationManager localization() { return localization; }
    public ThemeTokenManager themes() { return themes; }
    public PermissionManager permissions() { return permissions; }
    public ResourceGuard resources() { return resources; }
    public CacheManager cache() { return cache; }
    public BackupManager backups() { return backups; }
    public FreezeEngine freeze() { return freeze; }
    public BackgroundTaskManager backgroundTasks() { return backgroundTasks; }
    public ImportSecurityValidator importSecurity() { return importSecurity; }
    public DiagnosticCenter diagnostics() { return diagnostics; }
    public ClipboardService clipboard() { return clipboard; }
    public ProjectGraphManager projectGraph() { return projectGraph; }
    public VisualLayoutEngine visualLayout() { return visualLayout; }
    public StateVariantEngine stateVariants() { return stateVariants; }
    public AnimationEngine animations() { return animations; }
    public PreviewSandbox previewSandbox() { return previewSandbox; }
    public EditorContextStore editorContext() { return editorContext; }
    public AppLifecycleManager lifecycle() { return lifecycle; }
    public ImportMergeManager importMerge() { return importMerge; }
    public AutoRepairEngine autoRepair() { return autoRepair; }
    public ScaleBenchmarkHarness benchmark() { return benchmark; }
    public ToolLifecycleManager toolLifecycle() { return toolLifecycle; }
    public ProductCompletionServices completion() { return completion; }
    public ProductDeepContracts deep() { return deep; }
    public RepositoryInventory inventory() { return inventory; }
    public InputRouter inputRouter() { return inputRouter; }
    public ConditionalPropertyEngine conditionalProperties() { return conditionalProperties; }
    public DataProviderRegistry dataProviders() { return dataProviders; }
    public AssetLoadManager assetLoads() { return assetLoads; }
    public RenderDiagnostics renderDiagnostics() { return renderDiagnostics; }
    public ManagedAppProtocol managedAppProtocol() { return managedAppProtocol; }
    public VisibleArtifactManager visibleArtifacts() { return visibleArtifacts; }

    public boolean isReady() {
        return screens.startScreenId() != null
                && "id".equals(LocalizationManager.BAHASA_DEFAULT)
                && themes.get("token.color.neon") != null
                && resources.invariantPass()
                && projectGraph.generatedIndex().size() >= 1
                && visualLayout.snapshot().size() >= 2
                && !animations.all().isEmpty()
                && !previewSandbox.snapshot().isEmpty()
                && lifecycle.history().size() >= 1
                && toolLifecycle.activeCount() == 1
                && completion.isReady()
                && deep.isReady()
                && inventory.complete()
                && inputRouter.complete()
                && dataProviders.complete()
                && assetLoads.audit().isPass()
                && visibleArtifacts != null;
    }
}
