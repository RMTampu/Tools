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

    public boolean isReady() {
        return screens.startScreenId() != null
                && "id".equals(LocalizationManager.BAHASA_DEFAULT)
                && themes.get("token.color.neon") != null
                && resources.invariantPass();
    }
}
