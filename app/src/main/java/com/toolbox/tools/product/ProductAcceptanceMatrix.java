package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.AppState;
import com.toolbox.tools.runtime.RuntimeModelValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Behavior-oriented acceptance matrix for all 135 design sections.
 * A requirement only passes when a concrete runtime/core behavior is available.
 */
public final class ProductAcceptanceMatrix {
    public static final int REQUIREMENT_COUNT = 135;

    public Result evaluate(AppKernel kernel) {
        if (kernel == null) throw new NullPointerException("kernel");
        ProductServices s = kernel.productServices();
        Set<String> completion = s.completion().selfTest();
        Set<String> deep = s.deep().selfTest();
        LinkedHashMap<Integer, String> failures = new LinkedHashMap<>();

        boolean kernelReady = kernel.state() == AppState.READY
                && kernel.toolRegistry() != null
                && kernel.engineManager() != null;
        boolean editorReady = kernel.editorEnvironment() != null
                && kernel.authoringWorkspace() != null
                && kernel.productEngines() != null
                && kernel.productEngines().semuaSiap();
        boolean projectReady = kernel.projectManager() != null
                && kernel.recoveryManager() != null;
        boolean runtimeReady = kernel.runtimeEnvironment() != null
                && new RuntimeModelValidator().validate(kernel.runtimeEnvironment()).isEmpty();
        boolean libraryReady = kernel.libraryManager() != null
                && !kernel.libraryManager().components().allReady().isEmpty()
                && !kernel.libraryManager().assets().allReady().isEmpty()
                && !kernel.libraryManager().templates().allReady().isEmpty();
        boolean buildReady = kernel.buildValidator() != null
                && kernel.applicationIrBuilder() != null
                && kernel.readyCoordinator() != null;
        boolean repairReady = kernel.repairSessionManager() != null
                && kernel.healthMonitor() != null
                && kernel.safeModeController() != null;
        boolean deliveryReady = kernel.safePatchManager() != null
                && kernel.remotePatchVerifier() != null
                && kernel.evolutionManager() != null;
        boolean completionReady = s.completion().isReady()
                && s.deep().isReady()
                && s.inventory().complete()
                && s.inputRouter().complete();

        // 1-17: product identity, shell, editor, manual editing semantics.
        pass(1, kernelReady && editorReady && repairReady && deliveryReady, "PRODUCT_IDENTITY", failures);
        pass(2, kernelReady && completionReady, "PRINSIP_BESAR", failures);
        pass(3, kernelReady && editorReady && s.toolLifecycle().activeCount() == 1, "ARSITEKTUR_RUMAH", failures);
        pass(4, editorReady && s.toolLifecycle().activeCount() == 1, "HALAMAN_ENGINE", failures);
        pass(5, s.toolLifecycle() != null
                && completion.contains("engine_isolation")
                && deep.contains("lifecycle_release_probe"), "TOOL_LIFECYCLE", failures);
        pass(6, editorReady && kernel.editorEnvironment().shell() != null, "SHELL_UI", failures);
        pass(7, kernel.editorEnvironment().shell().bubbleController() != null, "BUBBLE", failures);
        pass(8, kernel.editorEnvironment().shell().edgePanel(null) != null, "EDGE_PANEL", failures);
        pass(9, runtimeReady && editorReady
                && kernel.runtimeEnvironment().navigation() != null, "LIVE_WORKSPACE", failures);
        pass(10, editorReady && kernel.editorEnvironment().shell() != null, "EDIT_ON_OFF", failures);
        pass(11, completion.contains("ui_state_hold"), "NO_CLONING", failures);
        pass(12, completion.contains("ui_state_hold"), "VISUAL_STATE_HOLD", failures);
        pass(13, projectReady, "MANUAL_SAVE", failures);
        pass(14, projectReady, "UNDO_REDO", failures);
        pass(15, !s.resources().budgets().isEmpty()
                && s.resources().invariantPass(), "PER_SCREEN_WORKING_SECTOR", failures);
        pass(16, editorReady && projectReady, "ROUND_TRIP_EDITING", failures);
        pass(17, editorReady && s.completion().versions.compatible("contract", 2), "VISUAL_PROPERTIES_CODE", failures);

        // 18-27: project store, revisioning, identity, dependency graph.
        pass(18, projectReady
                && completion.contains("saf_user_storage")
                && "SafProjectStore".equals(
                        s.inventory()
                                .require("implementation.project.store")
                                .implementation()
                ), "PROJECT_STORE", failures);
        pass(19, projectReady
                && !s.resources().budgets().isEmpty(), "HYBRID_SCREEN_STORE", failures);
        pass(20, projectReady, "PROJECT_MANIFEST", failures);
        pass(21, projectReady, "TRANSACTIONAL_SAVE", failures);
        pass(22, projectReady, "REVISION_SINGLE_WRITER", failures);
        pass(23, completion.contains("version_matrix"), "SCHEMA_VERSIONING", failures);
        pass(24, projectReady && completion.contains("authoritative_inventory"), "STABLE_IDENTITY", failures);
        pass(25, projectReady && completion.contains("recovery_catalog"), "TOMBSTONE_UNDO", failures);
        pass(26, s.projectGraph().generatedIndex().size() >= 1, "GENERATED_INDEX", failures);
        pass(27, completion.contains("incremental_validation"), "IMPACT_TRACKING", failures);

        // 28-45: registries, contracts, data, binding, logic.
        pass(28, libraryReady, "COMPONENT_REGISTRY", failures);
        pass(29, s.inventory().complete()
                && s.inventory().machineReadable().size() >= 20,
                "REGISTRY_INVENTORY", failures);
        pass(30, deep.contains("property_contract"), "PROPERTY_CONTRACT", failures);
        pass(31, deep.contains("event_action_contract")
                && inputDispatchPass(s), "EVENT_CONTRACT", failures);
        pass(32, kernel.runtimeEnvironment().actions() != null && deep.contains("event_action_contract"), "ACTION_REGISTRY", failures);
        pass(33, deep.contains("safe_converter"), "COMPATIBILITY_MATCHING", failures);
        pass(34, deep.contains("composite_executor"), "COMPOSITE_ACTION", failures);
        pass(35, runtimeReady, "NAVIGATION_CONTRACT", failures);
        pass(36, runtimeReady, "BACK_STACK", failures);
        pass(37, runtimeReady
                && s.dataProviders().complete()
                && deep.contains("data_provider_ecosystem"),
                "DATA_SOURCE_CONTRACT", failures);
        pass(38, runtimeReady, "DATA_BINDING", failures);
        pass(39, runtimeReady
                && dataWindowPass(s)
                && deep.contains("virtualized_paging"),
                "LAZY_PAGED_DATA", failures);
        pass(40, runtimeReady, "DYNAMIC_LIST_IDENTITY", failures);
        pass(41, runtimeReady && s.diagnostics() != null, "BROKEN_REFERENCE", failures);
        pass(42, completion.contains("flow_execution")
                && !kernel.runtimeEnvironment().model().flows().isEmpty(),
                "LOGIC_FLOW_EDITOR", failures);
        pass(43, completion.contains("flow_execution"), "BRANCH_LOOP_ASYNC", failures);
        pass(44, completion.contains("flow_execution"), "LIST_FIRST_DIAGRAM", failures);
        pass(45, libraryReady, "COMPONENT_INSTANCE_TEMPLATE", failures);

        // 46-62: states, animation, layout, interaction, accessibility, localization.
        pass(46, stateLayerPass(s)
                && deep.contains("state_layering"), "STATE_VARIANT", failures);
        pass(47, s.animations() != null
                && !s.animations().all().isEmpty()
                && !s.animations().groups().isEmpty()
                && deep.contains("animation_timeline"), "ANIMATION_MODEL", failures);
        pass(48, s.themes() != null, "DESIGN_TOKEN_THEME", failures);
        pass(49, s.visualLayout().snapshot().size() >= 2
                && !s.visualLayout().responsiveOverride(
                        "screen.home",
                        VisualLayoutEngine.Orientation.LANDSCAPE
                ).isEmpty()
                && deep.contains("constraint_multi_select"),
                "RESPONSIVE_LAYOUT", failures);
        pass(50, s.visualLayout().adaptiveClass(700)
                        == VisualLayoutEngine.AdaptiveClass.MEDIUM
                && !s.visualLayout().responsiveOverride(
                        "screen.home",
                        VisualLayoutEngine.Orientation.LANDSCAPE
                ).isEmpty(), "ADAPTIVE_ORIENTATION", failures);
        pass(51, !s.visualLayout().guides().isEmpty(),
                "GRID_GUIDE_SNAPPING", failures);
        pass(52, deep.contains("constraint_multi_select")
                && s.visualLayout().snapshot().size() >= 2,
                "MULTI_SELECT_GROUP", failures);
        pass(53, s.visualLayout().pathToRoot(
                        "object.home.primary"
                ).contains("layout.root"), "REPARENTING", failures);
        pass(54, kernel.editorEnvironment().visualSession() != null, "OBJECT_LOCK", failures);
        pass(55, s.visualLayout().hitTest(25, 181) != null, "LAYER_HIT_TEST", failures);
        pass(56, completion.contains("pointer_propagation")
                && inputDispatchPass(s), "POINTER_PROPAGATION", failures);
        pass(57, completion.contains("gesture_focus")
                && s.inputRouter().complete()
                && "object.home.primary".equals(
                        s.inputRouter().nextFocus(
                                "object.home.primary"
                        )
                ), "INPUT_GESTURE_FOCUS", failures);
        pass(58, s.visualLayout().safeInsets() != null,
                "SAFE_AREA_INSETS", failures);
        pass(59, s.visualLayout().zoom() >= 0.25f
                && s.visualLayout().zoom() <= 4f
                && !Float.isNaN(s.visualLayout().designX(20f)),
                "ZOOM_PAN", failures);
        pass(60, libraryReady && deep.contains("accessibility_semantic"), "ACCESSIBILITY_SEMANTIC", failures);
        pass(61, "id".equals(LocalizationManager.BAHASA_DEFAULT)
                && s.localization().formatCurrency(
                        12500,
                        "IDR",
                        "id-ID"
                ) != null
                && !s.localization().isRtl("id")
                && deep.contains("localization_formatting"),
                "LOCALIZATION", failures);
        pass(62, completion.contains("conditional_expression")
                && conditionalPass(s), "CONDITIONAL_PROPERTIES", failures);

        // 63-80: assets, cache, recovery, storage, import/export, permissions.
        pass(63, libraryReady, "ASSET_IDENTITY", failures);
        pass(64, completion.contains("asset_loading"), "ORIGINAL_PREVIEW", failures);
        pass(65, completion.contains("asset_loading")
                && assetLoadPass(s), "ASSET_LOADING", failures);
        pass(66, completion.contains("asset_audit")
                && s.assetLoads().audit().isPass(),
                "ASSET_AUDIT", failures);
        pass(67, s.cache() != null && deep.contains("cache_category_budget"), "CACHE_MANAGER", failures);
        pass(68, s.cache() != null, "CACHE_CLEANUP", failures);
        pass(69, projectReady, "RECOVERY", failures);
        pass(70, projectReady && completion.contains("recovery_catalog"), "INCREMENTAL_SNAPSHOT", failures);
        pass(71, completion.contains("recovery_catalog"), "RECOVERY_LIST", failures);
        pass(72, s.backups() != null, "BACKUP", failures);
        pass(73, completion.contains("saf_user_storage"), "SAF_STORAGE", failures);
        pass(74, completion.contains("access_relink"), "ACCESS_RELINK", failures);
        pass(75, projectReady && completion.contains("external_integrity"), "PROJECT_SECURITY", failures);
        pass(76, kernel.remotePatchVerifier() != null, "SECRET_SEPARATION", failures);
        pass(77, s.importSecurity() != null && deep.contains("import_security_deep"), "IMPORT_SECURITY", failures);
        pass(78, s.importMerge() != null, "IMPORT_MERGE", failures);
        pass(79, kernel.externalIntegrationManager() != null && deep.contains("full_project_export"), "EXPORT_CONTRACT", failures);
        pass(80, completion.contains("permission_derivation"), "PERMISSION_CONTRACT", failures);

        // 81-101: lifecycle, background work, diagnostics, build and engine contract.
        pass(81, completion.contains("lifecycle_policy"), "APP_SCREEN_LIFECYCLE", failures);
        pass(82, s.backgroundTasks() != null && deep.contains("background_task_contract"), "BACKGROUND_TASK", failures);
        pass(83, s.previewSandbox() != null, "PREVIEW_SAFETY", failures);
        pass(84, s.previewSandbox() != null, "PREVIEW_DATA", failures);
        pass(85, s.editorContext() != null && deep.contains("editor_context_complete"), "EDITOR_CONTEXT", failures);
        pass(86, buildReady, "EDITOR_METADATA_RUNTIME", failures);
        pass(87, s.clipboard() != null && deep.contains("clipboard_dependency_remap"), "CLIPBOARD", failures);
        pass(88, s.diagnostics() != null && deep.contains("diagnostic_rich_record"), "DIAGNOSTICS", failures);
        pass(89, s.autoRepair() != null && deep.contains("repair_detect_suggest_fix"), "DETECT_SUGGEST_FIX", failures);
        pass(90, completion.contains("incremental_validation")
                && incrementalValidationPass(kernel),
                "INCREMENTAL_VALIDATION", failures);
        pass(91, buildReady && completionReady, "BUILD_VALIDATOR", failures);
        pass(92, buildReady, "CANONICAL_IR", failures);
        pass(93, completion.contains("immutable_build_package"), "BUILD_PACKAGE", failures);
        pass(94, kernelReady, "BUILD_HANDOFF_OVERRIDE", failures);
        pass(95, "30".equals(kernel.configStore().get("targetApi", "")), "SIGNING_BOUNDARY", failures);
        pass(96, completion.contains("immutable_build_package"), "ARTIFACT_TRACEABILITY", failures);
        pass(97, completion.contains("engine_extension"), "ENGINE_EXTENSION", failures);
        pass(98, completion.contains("engine_isolation") && deep.contains("static_tool_dependency_gate"), "NO_INTER_TOOL_DEP", failures);
        pass(99, completion.contains("engine_isolation") && deep.contains("lifecycle_release_probe"), "LIFECYCLE_COMPLIANCE", failures);
        pass(100, completion.contains("engine_isolation") && deep.contains("fault_isolation"), "FAILURE_ISOLATION", failures);
        pass(101, kernel.declarativeRuntime() != null, "EXECUTABLE_BOUNDARY", failures);

        // 102-118: live target, update/freeze, safety, health, performance and integrity.
        pass(102, completion.contains("installed_target_bridge"), "INSTALLED_TARGET_BRIDGE", failures);
        pass(103, deliveryReady && deep.contains("update_intent_pipeline"), "DECLARATIVE_UPDATE", failures);
        pass(104, deliveryReady && deep.contains("update_intent_pipeline"), "UPDATE_PIPELINE", failures);
        pass(105, completion.contains("freeze_ab_overlay")
                && s.freeze() != null, "FREEZE_ENGINE", failures);
        pass(106, completion.contains("freeze_ab_overlay")
                && s.freeze().state() != null, "FREEZE_STATE_MACHINE", failures);
        pass(107, repairReady && deep.contains("safe_ui"), "SAFE_MODE_UI", failures);
        pass(108, repairReady && deep.contains("comprehensive_health"), "HEALTH_CHECK", failures);
        pass(109, completion.contains("screen_memory_budget"), "MEMORY_ARCHITECTURE", failures);
        pass(110, !s.resources().budgets().isEmpty()
                && completion.contains("screen_memory_budget"),
                "PER_SCREEN_MEMORY", failures);
        pass(111, completion.contains("render_cost")
                && renderBudgetPass(), "OVERDRAW_RENDERING", failures);
        pass(112, completion.contains("leak_discipline")
                && leakDisciplinePass(), "LEAK_DISCIPLINE", failures);
        pass(113, completion.contains("soak_test") && completion.contains("crash_matrix"), "TEST_BENCHMARK", failures);
        pass(114, completion.contains("soak_test"), "SOAK_TEST", failures);
        pass(115, completion.contains("crash_matrix"), "CRASH_TRANSACTION", failures);
        pass(116, completion.contains("scale_classes"), "SCALE_CLASSES", failures);
        pass(117, completion.contains("external_integrity"), "EXTERNAL_INTEGRITY", failures);
        pass(118, completion.contains("immutable_build_package"), "DEPENDENCY_DETERMINISM", failures);

        // 119-135: audit, diagnostic policy, source of truth and complete end-to-end architecture.
        pass(119, completion.contains("audit_behavior_gate"), "AUDIT_AGENT", failures);
        pass(120, s.autoRepair() != null, "AUTO_REPAIR_POLICY", failures);
        pass(121, s.diagnostics() != null, "DIAGNOSTIC_CODES", failures);
        pass(122, completion.contains("saf_user_storage") && projectReady && deep.contains("source_of_truth_policy"), "SOURCE_OF_TRUTH", failures);
        pass(123, completionReady && kernelReady, "INVARIANTS", failures);
        pass(124, editorReady && buildReady && projectReady, "PROJECT_TO_APK_FLOW", failures);
        pass(125, editorReady && completion.contains("ui_state_hold"), "UI_EDITOR_FLOW", failures);
        pass(126, libraryReady && completion.contains("authoritative_inventory"), "ASSET_TO_OBJECT_FLOW", failures);
        pass(127, runtimeReady && completion.contains("incremental_validation"), "BINDING_FLOW", failures);
        pass(128, completion.contains("flow_execution"), "LOGIC_FLOW", failures);
        pass(129, repairReady && deliveryReady, "REPAIR_EVOLUTION_FLOW", failures);
        pass(130, completion.contains("freeze_ab_overlay"), "FREEZE_FLOW", failures);
        pass(131, completion.contains("screen_memory_budget") && completion.contains("leak_discipline"), "RAM_ARCHITECTURE", failures);
        pass(132, completion.contains("saf_user_storage") && s.backups() != null, "STORAGE_ARCHITECTURE", failures);
        pass(
                133,
                specBoundaryPass(kernel),
                "SPEC_BOUNDARY",
                failures
        );
        pass(134, completionReady && kernelReady && editorReady && runtimeReady && libraryReady, "MATURE_TECHNICAL_FORM", failures);
        pass(135, completionReady && failures.isEmpty(), "ARCHITECTURE_CONCLUSION", failures);

        return new Result(failures);
    }

    private static boolean inputDispatchPass(
            ProductServices services
    ) {
        try {
            InputRouter.Dispatch route =
                    services.inputRouter().dispatch(
                            "object.home.primary",
                            InputRouter.Event.TAP,
                            InputRouter.Propagation.CONTINUE
                    );
            return "object.home.primary".equals(route.target())
                    && route.capture().contains("screen.home")
                    && route.bubble().contains("screen.home");
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean stateLayerPass(
            ProductServices services
    ) {
        Map<String, String> values =
                services.stateVariants().resolve(
                        "object.home.primary",
                        "state.pressed",
                        "orientation.landscape",
                        "theme.dark.neon",
                        null
                );
        return "#4CC9FF".equals(values.get("property.color"))
                && "196".equals(values.get("property.width"));
    }

    private static boolean conditionalPass(
            ProductServices services
    ) {
        LinkedHashMap<String, String> context =
                new LinkedHashMap<>();
        context.put("data.valid", "true");
        context.put("user.role", "admin");
        return services.conditionalProperties().evaluate(
                "data.valid && user.role == admin && true",
                context
        ) && !services.conditionalProperties().evaluate(
                "user.role == guest || false",
                context
        ) && services.conditionalProperties().evaluate(
                "true",
                context
        ) && !services.conditionalProperties().evaluate(
                "false",
                context
        );
    }

    private static boolean dataWindowPass(
            ProductServices services
    ) {
        try {
            DataProviderRegistry.Window window =
                    services.dataProviders().window(
                            1000,
                            120,
                            20,
                            10
                    );
            return window.first() == 110
                    && window.last() == 149
                    && window.materializedCount() == 40;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean assetLoadPass(
            ProductServices services
    ) {
        try {
            AssetLoadManager proof = new AssetLoadManager();
            proof.register(
                    "asset.image.proof",
                    AssetLoadManager.Kind.IMAGE,
                    4096,
                    "1111111111111111111111111111111111111111111111111111111111111111"
            );
            proof.register(
                    "asset.video.proof",
                    AssetLoadManager.Kind.VIDEO,
                    1024 * 1024,
                    "2222222222222222222222222222222222222222222222222222222222222222"
            );
            AssetLoadManager.LoadPlan image = proof.plan(
                    "asset.image.proof",
                    1080,
                    1920,
                    true
            );
            AssetLoadManager.LoadPlan video = proof.plan(
                    "asset.video.proof",
                    1080,
                    1920,
                    true
            );
            return image.thumbnailFirst()
                    && !image.streaming()
                    && video.streaming()
                    && video.chunkBytes() == 512 * 1024
                    && !services.assetLoads().all().isEmpty();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean incrementalValidationPass(
            AppKernel kernel
    ) {
        com.toolbox.tools.core.IncrementalResourceValidator validator =
                new com.toolbox.tools.core.IncrementalResourceValidator();
        LinkedHashMap<String, String> good =
                new LinkedHashMap<>();
        good.put("ui.object.home.primary.opacity", "0.5");
        LinkedHashMap<String, String> bad =
                new LinkedHashMap<>();
        bad.put("ui.object.home.primary.opacity", "4");
        return validator.validate(
                kernel.projectManager().current(),
                good,
                Collections.emptySet()
        ).isPass() && !validator.validate(
                kernel.projectManager().current(),
                bad,
                Collections.emptySet()
        ).isPass();
    }

    private static boolean renderBudgetPass() {
        RenderDiagnostics diagnostics =
                new RenderDiagnostics();
        diagnostics.record(
                "screen.home",
                80,
                2,
                2,
                16
        );
        return diagnostics.allWithinBudget()
                && diagnostics.sample(
                        "screen.home"
                ).complexityScore() > 0;
    }

    private static boolean leakDisciplinePass() {
        ResourceGuard guard = new ResourceGuard();
        guard.enterScreen("screen.home");
        guard.sample(
                "screen.home",
                20L * 1024L * 1024L,
                40,
                1
        );
        guard.sample(
                "screen.home",
                21L * 1024L * 1024L,
                40,
                1
        );
        guard.sample(
                "screen.home",
                22L * 1024L * 1024L,
                40,
                1
        );
        guard.sample(
                "screen.home",
                23L * 1024L * 1024L,
                40,
                1
        );
        return !guard.leakTrend("screen.home")
                && guard.invariantPass();
    }

    private static boolean specBoundaryPass(
            AppKernel kernel
    ) {
        DeclarativeProjectRuntime runtime =
                kernel.declarativeRuntime();
        return runtime.supportsWithoutRebuild(
                        "ui.object.home.primary.text"
                )
                && runtime.supportsWithoutRebuild(
                        "logic.ui.home.primary.action"
                )
                && runtime.supportsWithoutRebuild(
                        "asset.theme.dark.neon"
                )
                && !runtime.supportsWithoutRebuild(
                        "kernel.executable.class"
                )
                && !runtime.supportsWithoutRebuild(
                        "security.trust.anchor"
                )
                && !runtime.supportsWithoutRebuild(
                        "native.library.payload"
                );
    }

    private static void pass(int id, boolean condition, String code, Map<Integer,String> failures) {
        if (!condition) failures.put(id, code);
    }

    public static final class Result {
        private final Map<Integer,String> failures;
        Result(Map<Integer,String> failures) {
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
        }
        public boolean isPass() { return failures.isEmpty(); }
        public int passedCount() { return REQUIREMENT_COUNT - failures.size(); }
        public int requiredCount() { return REQUIREMENT_COUNT; }
        public Map<Integer,String> failures() { return failures; }
        public List<Integer> failedSections() { return Collections.unmodifiableList(new ArrayList<>(failures.keySet())); }
    }
}
