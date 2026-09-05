package com.toolbox.tools.core;

import com.toolbox.tools.authoring.AuthoringItemKind;
import com.toolbox.tools.authoring.AuthoringSearchResult;
import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.build.ApplicationIr;
import com.toolbox.tools.build.BuildValidationResult;
import com.toolbox.tools.build.CandidateIdentity;
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
import com.toolbox.tools.live.CapabilityArea;
import com.toolbox.tools.live.CapabilityAvailability;
import com.toolbox.tools.live.CapabilityScanResult;
import com.toolbox.tools.runtime.RenderTree;
import com.toolbox.tools.runtime.Renderer;
import com.toolbox.tools.runtime.RuntimeModelValidator;
import com.toolbox.tools.repair.HealthState;
import com.toolbox.tools.repair.RepairPlan;
import com.toolbox.tools.product.FullProductVerifier;

import java.util.Collections;
import java.util.List;

public final class VerificationManager {
    public VerificationResult verify(AppKernel kernel) {
        if (kernel == null) {
            return VerificationResult.fail("kernel tidak tersedia");
        }
        if (kernel.state() != AppState.READY) {
            return VerificationResult.fail("kernel belum siap");
        }
        if (!kernel.toolRegistry().contains("foundation")
                || !kernel.engineManager().contains("foundation-engine")) {
            return VerificationResult.fail("fondasi tidak tersedia");
        }
        if (!"30".equals(kernel.configStore().get("targetApi", ""))
                || !"arm64".equals(kernel.configStore().get("targetAbi", ""))) {
            return VerificationResult.fail("target Android tidak cocok");
        }
        if (!"produk-penuh-v13-maksimal".equals(
                kernel.configStore().get("tahap", "")
        )) {
            return VerificationResult.fail(
                    "konfigurasi produk penuh v13 maksimal tidak tersedia"
            );
        }
        if (!"id".equals(kernel.configStore().get("bahasaDefault", ""))) {
            return VerificationResult.fail("bahasa default bukan Bahasa Indonesia");
        }
        if (kernel.recoveryManager().isRecoveryRequired()) {
            return VerificationResult.fail("pemulihan diperlukan");
        }

        ProjectState project = kernel.projectManager().current();
        if (!"project.default".equals(project.projectId())
                || project.schemaVersion() != ProjectState.CURRENT_SCHEMA_VERSION
                || project.buildModelVersion()
                != ProjectState.CURRENT_BUILD_MODEL_VERSION) {
            return VerificationResult.fail("kontrak proyek tidak cocok");
        }

        if (kernel.libraryManager().resolveExact(
                new LibraryKey(
                        LibraryItemType.COMPONENT,
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
        ) == null) {
            return VerificationResult.fail("komponen bawaan tidak tersedia");
        }

        if (!new RuntimeModelValidator()
                .validate(kernel.runtimeEnvironment())
                .isEmpty()) {
            return VerificationResult.fail("model runtime mempunyai diagnostik");
        }

        RenderTree tree = new Renderer().materialize(
                kernel.runtimeEnvironment().model().screen("screen.home"),
                kernel.runtimeEnvironment().components()
        );
        if (tree.nodes().size() != 1
                || !tree.diagnostics().isEmpty()
                || !tree.nodes().get(0).available()) {
            return VerificationResult.fail("materialisasi renderer gagal");
        }

        if (kernel.editorEnvironment() == null
                || kernel.authoringWorkspace() == null) {
            return VerificationResult.fail("workspace Editor tidak tersedia");
        }

        Object runtimeIdentity = kernel.authoringWorkspace().runtime();
        if (runtimeIdentity != kernel.runtimeEnvironment()) {
            return VerificationResult.fail("Editor tidak memakai model runtime yang sama");
        }

        for (AuthoringSection section : AuthoringSection.values()) {
            kernel.authoringWorkspace().activate(section);
            if (kernel.authoringWorkspace().activeSection() != section) {
                return VerificationResult.fail("aktivasi fungsi Editor gagal");
            }
        }
        kernel.authoringWorkspace().activate(AuthoringSection.UI);

        List<AuthoringSearchResult> componentSearch =
                kernel.authoringWorkspace().searchAll("component.button", 20);
        if (componentSearch.isEmpty()
                || componentSearch.get(0).kind() != AuthoringItemKind.COMPONENT) {
            return VerificationResult.fail("pencarian Stable ID terpadu gagal");
        }

        List<AuthoringSearchResult> broad =
                kernel.authoringWorkspace().searchAll("", 100);
        if (broad.size() < 6 || broad.size() > 100) {
            return VerificationResult.fail("pencarian terpadu tidak lengkap");
        }

        com.toolbox.tools.library.TemplateDefinition defaultTemplate =
                kernel.libraryManager().templates().resolveExact(
                        "template.screen.basic",
                        VersionNumber.parse("1.0.0")
                );
        if (defaultTemplate == null) {
            return VerificationResult.fail("template bawaan tidak tersedia");
        }
        com.toolbox.tools.library.TemplateInstantiationPlan preview =
                new com.toolbox.tools.library.TemplateInstantiationPlan(
                        defaultTemplate,
                        "preview.stage6"
                );
        if (!"preview.stage6.object.primary".equals(
                preview.identityMap().get("object.primary"))) {
            return VerificationResult.fail("pemetaan identitas template gagal");
        }

        List<AuthoringSearchResult> templateSearch =
                kernel.authoringWorkspace().searchAll(
                        "template.screen.basic",
                        20
                );
        if (templateSearch.isEmpty()
                || templateSearch.get(0).kind() != AuthoringItemKind.TEMPLATE) {
            return VerificationResult.fail("pencarian template terpadu gagal");
        }

        kernel.editorEnvironment().shell().setMode(EditorMode.PREVIEW);
        if (kernel.editorEnvironment().shell().editorOverlayVisible()) {
            return VerificationResult.fail("overlay Editor masih terlihat pada Pratinjau");
        }
        kernel.editorEnvironment().shell().setMode(EditorMode.EDIT);

        EdgePanelModel addPanel = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        if (addPanel.items().isEmpty()) {
            return VerificationResult.fail("panel Edge Editor tidak tersedia");
        }

        if (kernel.externalIntegrationManager() == null
                || !"Sumber Demo".equals(
                kernel.externalIntegrationManager().adapter().labelIndonesia())) {
            return VerificationResult.fail("adapter eksternal tidak tersedia");
        }

        ExternalSnapshot external = kernel.externalIntegrationManager()
                .demoSnapshot(1, "cursor.verification.1");
        NormalizationResult normalized = kernel.externalIntegrationManager()
                .importSnapshot(external);
        if (!normalized.isPass()
                || normalized.records().size() != 1
                || !"adapter.demo.item.alpha".equals(
                normalized.records().get(0).stableId())) {
            return VerificationResult.fail("normalisasi sumber eksternal gagal");
        }

        ExportPackage exported = kernel.externalIntegrationManager()
                .export(normalized.records());
        if (exported.sha256() == null
                || !exported.sha256().matches("[0-9a-f]{64}")
                || !exported.payload().startsWith("TBX_EXTERNAL_V1")) {
            return VerificationResult.fail("ekspor deterministik gagal");
        }

        SyncPlan sync = kernel.externalIntegrationManager().planSync(external);
        if (sync.status() == SyncStatus.CLEAN) {
            kernel.externalIntegrationManager().applySync(sync);
        } else if (sync.status() != SyncStatus.NO_CHANGE) {
            return VerificationResult.fail("sinkronisasi eksternal tidak aman");
        }
        SyncPlan same = kernel.externalIntegrationManager().planSync(external);
        if (same.status() != SyncStatus.NO_CHANGE) {
            return VerificationResult.fail("sinkronisasi tidak idempoten");
        }

        if (kernel.repairSessionManager() == null
                || kernel.recoveryPreviewService() == null
                || kernel.healthMonitor() == null) {
            return VerificationResult.fail("layanan perbaikan/pemulihan tidak tersedia");
        }

        RepairPlan checksumA = new RepairPlan(
                "repair.verification",
                project.projectId(),
                Math.max(1, kernel.projectManager().savedRevision()),
                Collections.singletonMap(
                        "screen.verification",
                        "verification"
                ),
                Collections.emptySet()
        );
        RepairPlan checksumB = new RepairPlan(
                "repair.verification",
                project.projectId(),
                Math.max(1, kernel.projectManager().savedRevision()),
                Collections.singletonMap(
                        "screen.verification",
                        "verification"
                ),
                Collections.emptySet()
        );
        if (!checksumA.checksum().equals(checksumB.checksum())
                || !checksumA.checksum().matches("[0-9a-f]{64}")) {
            return VerificationResult.fail("checksum perbaikan tidak deterministik");
        }

        HealthState health = kernel.healthMonitor().inspect(kernel).state();
        if (health == HealthState.RECOVERY_REQUIRED) {
            return VerificationResult.fail("kesehatan sistem meminta pemulihan");
        }

        if (kernel.capabilityScanner() == null
                || kernel.selfTargetDescriptor() == null
                || kernel.liveSessionManager() == null) {
            return VerificationResult.fail("layanan capability/live tidak tersedia");
        }

        CapabilityScanResult capabilityScan =
                kernel.capabilityScanner().scan(
                        kernel.selfTargetDescriptor()
                );
        if (!capabilityScan.installed()
                || !capabilityScan.liveAvailable()
                || capabilityScan.status(CapabilityArea.UI)
                != CapabilityAvailability.AVAILABLE
                || capabilityScan.status(CapabilityArea.LOGIC)
                != CapabilityAvailability.AVAILABLE
                || capabilityScan.status(CapabilityArea.RUNTIME)
                != CapabilityAvailability.AVAILABLE) {
            return VerificationResult.fail("kontrak Capability Scan tidak cocok");
        }

        if (!kernel.liveSessionManager()
                .selfEditPolicy()
                .isDeclarativeEditable("screen.live.verification")
                || kernel.liveSessionManager()
                .selfEditPolicy()
                .isDeclarativeEditable("kernel.security.core")) {
            return VerificationResult.fail("proteksi Edit ToolBox tidak cocok");
        }

        if (kernel.buildValidator() == null
                || kernel.applicationIrBuilder() == null
                || kernel.candidateIdentityFactory() == null
                || kernel.readyCoordinator() == null
                || kernel.remotePatchVerifier() == null
                || kernel.safePatchManager() == null) {
            return VerificationResult.fail("layanan build/evolusi produk tidak tersedia");
        }

        ProjectState beforeReadyPreview =
                kernel.projectManager().current();
        long beforeSavedRevision =
                kernel.projectManager().savedRevision();

        BuildValidationResult readyPreview =
                kernel.readyCoordinator().preview();
        if (!readyPreview.isPass()) {
            return VerificationResult.fail(
                    "pratinjau READY gagal:" + readyPreview.message()
            );
        }
        if (!beforeReadyPreview.equals(
                kernel.projectManager().current())
                || beforeSavedRevision
                != kernel.projectManager().savedRevision()) {
            return VerificationResult.fail(
                    "pratinjau READY mengubah proyek"
            );
        }

        ApplicationIr firstIr =
                kernel.applicationIrBuilder().build(kernel);
        ApplicationIr secondIr =
                kernel.applicationIrBuilder().build(kernel);
        if (firstIr.irVersion()
                != ApplicationIr.CURRENT_IR_VERSION
                || !firstIr.sha256().equals(secondIr.sha256())
                || !firstIr.canonical().equals(secondIr.canonical())
                || !firstIr.sha256().matches("[0-9a-f]{64}")) {
            return VerificationResult.fail(
                    "kontrak IR deterministik gagal"
            );
        }

        String previewUnsigned =
                "0000000000000000000000000000000000000000000000000000000000000000";
        String parentSigned =
                "f9dcffed7dc5d657c6dbd1c45933db6a4f6215f5145aee1849cc50f35038b76b";
        CandidateIdentity firstCandidate =
                kernel.candidateIdentityFactory().create(
                        "com.toolbox.tools",
                        13,
                        "13.0-produk-penuh-maksimal",
                        parentSigned,
                        firstIr.sha256(),
                        previewUnsigned
                );
        CandidateIdentity secondCandidate =
                kernel.candidateIdentityFactory().create(
                        "com.toolbox.tools",
                        12,
                        "12.0-produk-penuh",
                        parentSigned,
                        firstIr.sha256(),
                        previewUnsigned
                );
        if (!firstCandidate.sha256().equals(
                secondCandidate.sha256())
                || !firstCandidate.candidateId().equals(
                secondCandidate.candidateId())) {
            return VerificationResult.fail(
                    "identitas kandidat tidak deterministik"
            );
        }

        FullProductVerifier.Result full =
                new FullProductVerifier().verify(kernel);
        if (!full.isPass()) {
            return VerificationResult.fail(
                    "produk belum lengkap: "
                            + full.available().size()
                            + "/" + full.requiredCount()
            );
        }
        if (kernel.engineManager().snapshot().size() < 6
                || !kernel.productEngines().semuaSiap()
                || kernel.libraryManager().components().allReady().size() < 18
                || kernel.libraryManager().assets().allReady().size() < 5
                || kernel.libraryManager().templates().allReady().size() < 4) {
            return VerificationResult.fail("inventaris produk penuh tidak lengkap");
        }

        return VerificationResult.pass(
                "produk penuh siap • 5 engine • Bahasa Indonesia • App.patch"
        );
    }
}
