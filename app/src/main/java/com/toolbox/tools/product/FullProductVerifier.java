package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.engine.ProductEngineSuite;
import com.toolbox.tools.runtime.RuntimeModelValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class FullProductVerifier {
    public static final class Result {
        private final Set<ProductCapability> available;
        private final List<String> errors;

        Result(
                Set<ProductCapability> available,
                List<String> errors
        ) {
            this.available = Collections.unmodifiableSet(
                    EnumSet.copyOf(available)
            );
            this.errors = Collections.unmodifiableList(
                    new ArrayList<>(errors)
            );
        }

        public boolean isPass() {
            return errors.isEmpty()
                    && available.size() == ProductCapability.values().length;
        }

        public Set<ProductCapability> available() { return available; }
        public List<String> errors() { return errors; }
        public int requiredCount() { return ProductCapability.values().length; }
    }

    public Result verify(AppKernel kernel) {
        EnumSet<ProductCapability> ok =
                EnumSet.noneOf(ProductCapability.class);
        List<String> errors = new ArrayList<>();

        add(ok, ProductCapability.KERNEL_DAN_REGISTRY,
                kernel.toolRegistry() != null && kernel.engineManager() != null);
        add(ok, ProductCapability.LIFECYCLE_DAN_ISOLASI,
                kernel.state() != null && kernel.engineManager().snapshot().size() >= 6);
        add(ok, ProductCapability.PROYEK_DAN_STABLE_ID,
                kernel.projectManager() != null);
        add(ok, ProductCapability.SIMPAN_TRANSAKSIONAL,
                kernel.projectManager() != null);
        add(ok, ProductCapability.UNDO_REDO_DAN_REVISI,
                kernel.projectManager() != null);
        add(ok, ProductCapability.MIGRASI_DAN_RECOVERY,
                kernel.recoveryManager() != null);
        add(ok, ProductCapability.LIBRARY_KOMPONEN,
                kernel.libraryManager().components().allReady().size() >= 18);
        add(ok, ProductCapability.LIBRARY_ASET,
                kernel.libraryManager().assets().allReady().size() >= 5);
        add(ok, ProductCapability.TEMPLATE_DAN_DEPENDENCY,
                kernel.libraryManager().templates().allReady().size() >= 4);
        add(ok, ProductCapability.RENDERER,
                new RuntimeModelValidator().validate(
                        kernel.runtimeEnvironment()
                ).isEmpty());
        add(ok, ProductCapability.NAVIGASI,
                !kernel.runtimeEnvironment().model().screens().isEmpty());
        add(ok, ProductCapability.EVENT_DAN_ACTION,
                kernel.runtimeEnvironment().actions() != null);
        add(ok, ProductCapability.DATA,
                !kernel.runtimeEnvironment().model().dataSources().isEmpty());
        add(ok, ProductCapability.BINDING,
                !kernel.runtimeEnvironment().model().bindings().isEmpty());
        add(ok, ProductCapability.LOGIKA_FLOW,
                !kernel.runtimeEnvironment().model().flows().isEmpty());

        ProductEngineSuite engines = kernel.productEngines();
        add(ok, ProductCapability.EDITOR_UI, engines.ui().isReady());
        add(ok, ProductCapability.EDITOR_LOGIKA, engines.logic().isReady());
        add(ok, ProductCapability.EDITOR_DATA, engines.data().isReady());
        add(ok, ProductCapability.EDITOR_BINDING, engines.binding().isReady());
        add(ok, ProductCapability.EDITOR_ASET, engines.asset().isReady());

        add(ok, ProductCapability.BUBBLE,
                kernel.editorEnvironment().shell().bubble() != null);
        add(ok, ProductCapability.EDGE_PANEL,
                kernel.editorEnvironment().shell().edgePanel() != null);
        add(ok, ProductCapability.FLOATING_EDITOR,
                kernel.editorEnvironment().floating() != null);
        add(ok, ProductCapability.VISUAL_PROPERTI_KODE,
                kernel.authoringWorkspace() != null);
        add(ok, ProductCapability.EDIT_PRATINJAU_UJI_LANGSUNG,
                kernel.editorEnvironment().shell() != null);

        ProductServices services = kernel.productServices();
        add(ok, ProductCapability.DIAGNOSTIK, services.diagnostics() != null);
        add(ok, ProductCapability.AKSESIBILITAS,
                kernel.libraryManager().components().allReady().size() >= 18);
        add(ok, ProductCapability.TEMA_DAN_TOKEN,
                services.themes().snapshot().size() >= 6);
        add(ok, ProductCapability.LOKALISASI_INDONESIA,
                "id".equals(LocalizationManager.BAHASA_DEFAULT));
        add(ok, ProductCapability.SCREEN_DAN_RESPONSIVE,
                services.screens().all().size() >= 2);
        add(ok, ProductCapability.IZIN, services.permissions() != null);
        add(ok, ProductCapability.CACHE_DAN_RESOURCE_GUARD,
                services.resources().invariantPass());
        add(ok, ProductCapability.BACKUP, services.backups() != null);
        add(ok, ProductCapability.FREEZE, services.freeze() != null);
        add(ok, ProductCapability.IMPORT_EKSTERNAL,
                kernel.externalIntegrationManager() != null);
        add(ok, ProductCapability.EXPORT_DAN_SYNC,
                kernel.externalIntegrationManager() != null);
        add(ok, ProductCapability.CAPABILITY_SCAN,
                kernel.capabilityScanner() != null);
        add(ok, ProductCapability.LIVE_DAN_TERAPKAN,
                kernel.liveSessionManager() != null);
        add(ok, ProductCapability.SELF_EDIT_TERPROTEKSI,
                kernel.selfTargetDescriptor() != null);
        add(ok, ProductCapability.REPAIR_DAN_HEALTH,
                kernel.repairSessionManager() != null
                        && kernel.healthMonitor() != null);
        add(ok, ProductCapability.BUILD_VALIDATOR_DAN_IR,
                kernel.readyCoordinator() != null
                        && kernel.applicationIrBuilder() != null);
        add(ok, ProductCapability.APP_PATCH,
                kernel.safePatchManager() != null);
        add(ok, ProductCapability.VERIFIKASI_REMOTE,
                kernel.remotePatchVerifier() != null);
        add(ok, ProductCapability.SAFE_RESTORE,
                kernel.recoveryPreviewService() != null);
        add(ok, ProductCapability.SAFE_MODE,
                kernel.safeModeController() != null);
        add(ok, ProductCapability.BACKGROUND_TASK,
                services.backgroundTasks() != null);
        add(ok, ProductCapability.KEAMANAN_IMPORT,
                services.importSecurity() != null);
        add(ok, ProductCapability.CLIPBOARD_STABLE_ID,
                services.clipboard() != null);

        for (ProductCapability capability : ProductCapability.values()) {
            if (!ok.contains(capability)) {
                errors.add("KOMPONEN_WAJIB_HILANG:" + capability.name());
            }
        }
        return new Result(ok, errors);
    }

    private static void add(
            Set<ProductCapability> out,
            ProductCapability capability,
            boolean condition
    ) {
        if (condition) out.add(capability);
    }
}
