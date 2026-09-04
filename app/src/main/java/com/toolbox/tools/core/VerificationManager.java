package com.toolbox.tools.core;

import com.toolbox.tools.authoring.AuthoringItemKind;
import com.toolbox.tools.authoring.AuthoringSearchResult;
import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.editor.EdgePanelModel;
import com.toolbox.tools.editor.EditorMode;
import com.toolbox.tools.editor.VisualCapabilitySet;
import com.toolbox.tools.integration.ExportPackage;
import com.toolbox.tools.integration.ExternalSnapshot;
import com.toolbox.tools.integration.NormalizationResult;
import com.toolbox.tools.integration.SyncPlan;
import com.toolbox.tools.integration.SyncStatus;
import com.toolbox.tools.library.LibraryItemType;
import com.toolbox.tools.library.LibraryKey;
import com.toolbox.tools.library.VersionNumber;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import java.util.Collections;
import java.util.List;

public final class VerificationManager {
    public VerificationResult verify(AppKernel kernel) {
        if (kernel == null) {
            return VerificationResult.fail("kernel missing");
        }
        if (kernel.state() != AppState.READY) {
            return VerificationResult.fail("kernel not ready");
        }
        if (!kernel.toolRegistry().contains("foundation")
                || !kernel.engineManager().contains("foundation-engine")) {
            return VerificationResult.fail("foundation unavailable");
        }
        if (!"30".equals(kernel.configStore().get("targetApi", ""))
                || !"arm64".equals(kernel.configStore().get("targetAbi", ""))) {
            return VerificationResult.fail("android target mismatch");
        }
        if (!"7".equals(kernel.configStore().get("tahap", ""))) {
            return VerificationResult.fail("tahap 7 configuration missing");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("recovery required");
        }

        ProjectState project = kernel.projectManager().current();
        if (!"project.default".equals(project.projectId())
                || project.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION
                || project.buildModelVersion()
                != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            return VerificationResult.fail("project contract mismatch");
        }

        if (kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.COMPONENT,
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
        ) == null) {
            return VerificationResult.fail("default component unavailable");
        }

        if (!new RuntimeModelValidator()
                .validate(kernel.runtimeEnvironment())
                .isEmpty()) {
            return VerificationResult.fail("runtime model diagnostics present");
        }

        RenderTree tree = new Renderer().materialize(
                kernel.runtimeEnvironment().model().screen("screen.home"),
                kernel.runtimeEnvironment().components()
        );
        if (tree.nodes().size() != 1
                || !tree.diagnostics().isEmpty()
                || !tree.nodes().get(0).available()) {
            return VerificationResult.fail("renderer materialization failed");
        }

        if (kernel.editorEnvironment() == null
                || kernel.authoringWorkspace() == null) {
            return VerificationResult.fail("editor/authoring workspace missing");
        }

        Object runtimeIdentity = kernel.authoringWorkspace().runtime();
        if (runtimeIdentity != kernel.runtimeEnvironment()) {
            return VerificationResult.fail("authoring cloned runtime model");
        }

        for (AuthoringSection section : AuthoringSection.values()) {
            kernel.authoringWorkspace().activate(section);
            if (kernel.authoringWorkspace().activeSection() != section) {
                return VerificationResult.fail("authoring section activation failed");
            }
        }
        kernel.authoringWorkspace().activate(AuthoringSection.UI);

        List<AuthoringSearchResult> componentSearch =
                kernel.authoringWorkspace().searchAll("component.button", 20);
        if (componentSearch.isEmpty()
                || componentSearch.get(0).kind() != AuthoringItemKind.COMPONENT) {
            return VerificationResult.fail("unified stable-id search failed");
        }

        List<AuthoringSearchResult> broad =
                kernel.authoringWorkspace().searchAll("", 100);
        if (broad.size() < 6 || broad.size() > 100) {
            return VerificationResult.fail("unified search closure failed");
        }

        com.toolbox.tools.library.TemplateDefinition defaultTemplate =
                kernel.libraryManager().templates().resolveExact(
                        "template.screen.basic",
                        VersionNumber.parse("1.0.0")
                );
        if (defaultTemplate == null) {
            return VerificationResult.fail("template authoring source missing");
        }
        com.toolbox.tools.library.TemplateInstantiationPlan preview =
                new com.toolbox.tools.library.TemplateInstantiationPlan(
                        defaultTemplate,
                        "preview.stage6"
                );
        if (!"preview.stage6.object.primary".equals(
                preview.identityMap().get("object.primary"))) {
            return VerificationResult.fail("template preview identity map failed");
        }

        List<AuthoringSearchResult> templateSearch =
                kernel.authoringWorkspace().searchAll(
                        "template.screen.basic",
                        20
                );
        if (templateSearch.isEmpty()
                || templateSearch.get(0).kind() != AuthoringItemKind.TEMPLATE) {
            return VerificationResult.fail("template unified search failed");
        }

        kernel.editorEnvironment().shell().setMode(EditorMode.PREVIEW);
        if (kernel.editorEnvironment().shell().editorOverlayVisible()) {
            return VerificationResult.fail("preview overlay visible");
        }
        kernel.editorEnvironment().shell().setMode(EditorMode.EDIT);

        EdgePanelModel addPanel = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        if (addPanel.items().isEmpty()) {
            return VerificationResult.fail("edge authoring panel missing");
        }

        if (kernel.externalIntegrationManager() == null
                || !"Sumber Demo".equals(
                kernel.externalIntegrationManager().adapter().labelIndonesia())) {
            return VerificationResult.fail("external adapter unavailable");
        }

        ExternalSnapshot external = kernel.externalIntegrationManager()
                .demoSnapshot(1, "cursor.verification.1");
        NormalizationResult normalized = kernel.externalIntegrationManager()
                .importSnapshot(external);
        if (!normalized.isPass()
                || normalized.records().size() != 1
                || !"adapter.demo.item.alpha".equals(
                normalized.records().get(0).stableId())) {
            return VerificationResult.fail("external normalization failed");
        }

        ExportPackage exported = kernel.externalIntegrationManager()
                .export(normalized.records());
        if (exported.sha256() == null
                || !exported.sha256().matches("[0-9a-f]{64}")
                || !exported.payload().startsWith("TBX_EXTERNAL_V1")) {
            return VerificationResult.fail("deterministic export failed");
        }

        SyncPlan sync = kernel.externalIntegrationManager().planSync(external);
        if (sync.status() == SyncStatus.CLEAN) {
            kernel.externalIntegrationManager().applySync(sync);
        } else if (sync.status() != SyncStatus.NO_CHANGE) {
            return VerificationResult.fail("sync verification not clean/idempotent");
        }
        SyncPlan same = kernel.externalIntegrationManager().planSync(external);
        if (same.status() != SyncStatus.NO_CHANGE) {
            return VerificationResult.fail("sync idempotency failed");
        }

        return VerificationResult.pass("tahap 7 external integration ready");
    }
}
