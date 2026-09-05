package com.toolbox.tools.ui;

public interface WorkspaceHostActions {
    void requestExternalAsset();
    String externalAssetStatus();
    void requestEvolutionPackage();
    String evolutionPackageStatus();

    boolean launchInstalledTarget(
            String packageName,
            String editDoor,
            String sessionId,
            String projectId,
            long revision
    );

    boolean openManagedTargetEditor(
            String packageName,
            String providerAuthority,
            String projectId
    );

    boolean returnToToolBoxProject();

    boolean externalTargetActive();
}
