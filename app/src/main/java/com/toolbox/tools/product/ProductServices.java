package com.toolbox.tools.product;

import com.toolbox.tools.core.ProjectManager;
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

    public ProductServices(ProjectManager projects) {
        Objects.requireNonNull(projects, "projects");
        screens = new ScreenManager();
        localization = new LocalizationManager();
        themes = new ThemeTokenManager();
        permissions = new PermissionManager();
        resources = new ResourceGuard();
        cache = new CacheManager();
        backups = new BackupManager(projects);
        freeze = new FreezeEngine(projects);
        backgroundTasks = new BackgroundTaskManager();
        importSecurity = new ImportSecurityValidator();
        diagnostics = new DiagnosticCenter();
        clipboard = new ClipboardService();
        projectGraph = new ProjectGraphManager();
        visualLayout = new VisualLayoutEngine();
        stateVariants = new StateVariantEngine();
        animations = new AnimationEngine();
        previewSandbox = new PreviewSandbox();
        editorContext = new EditorContextStore();
        lifecycle = new AppLifecycleManager();
        importMerge = new ImportMergeManager();
        autoRepair = new AutoRepairEngine();
        benchmark = new ScaleBenchmarkHarness();
        toolLifecycle = new ToolLifecycleManager();

        projectGraph.registerEntity("screen.home");
        projectGraph.registerEntity("screen.detail");
        projectGraph.registerEntity("object.home.primary");
        projectGraph.link("object.home.primary", "screen.detail");

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

        animations.register(new AnimationEngine.Animation(
                "animation.button.press",
                AnimationEngine.Kind.SCALE,
                "event.click",
                160,
                0,
                AnimationEngine.Easing.EASE_OUT
        ));
        previewSandbox.putMock("mock.user.name", "Pengguna");
        lifecycle.emit(AppLifecycleManager.Event.APP_START, null);

        for (String tool : new String[]{
                "tool.ui","tool.logic","tool.data","tool.binding","tool.asset"
        }) {
            toolLifecycle.register(tool);
        }
        toolLifecycle.load("tool.ui");
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
                && toolLifecycle.activeCount() == 1;
    }
}
