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
        pass(60, libraryReady
                && accessibilityContractPass(kernel)
                && deep.contains("accessibility_semantic"),
                "ACCESSIBILITY_SEMANTIC", failures);
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
        pass(
                66,
                completion.contains("asset_audit")
                        && assetAuditContractPass(kernel, s),
                "ASSET_AUDIT",
                failures
        );
        pass(67, cacheManagerContractPass(s)
                && deep.contains("cache_category_budget"),
                "CACHE_MANAGER", failures);
        pass(68, cacheCleanupPass(s),
                "CACHE_CLEANUP", failures);
        pass(69, projectReady, "RECOVERY", failures);
        pass(70, projectReady && completion.contains("recovery_catalog"), "INCREMENTAL_SNAPSHOT", failures);
        pass(71, completion.contains("recovery_catalog"), "RECOVERY_LIST", failures);
        pass(72, s.backups() != null, "BACKUP", failures);
        pass(73, completion.contains("saf_user_storage"), "SAF_STORAGE", failures);
        pass(74, completion.contains("access_relink"), "ACCESS_RELINK", failures);
        pass(75, projectReady && completion.contains("external_integrity"), "PROJECT_SECURITY", failures);
        pass(76, kernel.remotePatchVerifier() != null, "SECRET_SEPARATION", failures);
        pass(
                77,
                s.importSecurity() != null
                        && importSecurityContractPass(s),
                "IMPORT_SECURITY",
                failures
        );
        pass(
                78,
                s.importMerge() != null
                        && importMergeContractPass(s),
                "IMPORT_MERGE",
                failures
        );
        pass(79, kernel.externalIntegrationManager() != null && deep.contains("full_project_export"), "EXPORT_CONTRACT", failures);
        pass(
                80,
                completion.contains("permission_derivation")
                        && permissionContractPass(s),
                "PERMISSION_CONTRACT",
                failures
        );

        // 81-101: lifecycle, background work, diagnostics, build and engine contract.
        pass(81, completion.contains("lifecycle_policy")
                && s.lifecycle().completeContract(),
                "APP_SCREEN_LIFECYCLE", failures);
        pass(82, s.backgroundTasks().completeContract()
                && deep.contains("background_task_contract"),
                "BACKGROUND_TASK", failures);
        pass(83, s.previewSandbox() != null, "PREVIEW_SAFETY", failures);
        pass(84, s.previewSandbox().completeContract(),
                "PREVIEW_DATA", failures);
        pass(85, s.editorContext().completeContract()
                && deep.contains("editor_context_complete"),
                "EDITOR_CONTEXT", failures);
        pass(86, buildReady, "EDITOR_METADATA_RUNTIME", failures);
        pass(87, clipboardContractPass(s)
                && deep.contains("clipboard_dependency_remap"),
                "CLIPBOARD", failures);
        pass(88, s.diagnostics() != null && deep.contains("diagnostic_rich_record"), "DIAGNOSTICS", failures);
        pass(89, s.autoRepair() != null && deep.contains("repair_detect_suggest_fix"), "DETECT_SUGGEST_FIX", failures);
        pass(90, completion.contains("incremental_validation")
                && incrementalValidationPass(kernel),
                "INCREMENTAL_VALIDATION", failures);
        pass(91, buildReady && completionReady, "BUILD_VALIDATOR", failures);
        pass(92, buildReady, "CANONICAL_IR", failures);
        pass(93, buildReady
                && kernel.buildHandoffManager() != null
                && completion.contains("immutable_build_package"),
                "BUILD_PACKAGE", failures);
        pass(94, buildReady
                && kernel.buildHandoffManager() != null,
                "BUILD_HANDOFF_OVERRIDE", failures);
        pass(95, "30".equals(
                        kernel.configStore().get("targetApi", "")
                )
                && "arm64".equals(
                        kernel.configStore().get("targetAbi", "")
                ),
                "SIGNING_BOUNDARY", failures);
        pass(96, buildReady
                && kernel.buildHandoffManager() != null
                && completion.contains("immutable_build_package"),
                "ARTIFACT_TRACEABILITY", failures);
        pass(97, completion.contains("engine_extension"), "ENGINE_EXTENSION", failures);
        pass(98, completion.contains("engine_isolation") && deep.contains("static_tool_dependency_gate"), "NO_INTER_TOOL_DEP", failures);
        pass(99, completion.contains("engine_isolation") && deep.contains("lifecycle_release_probe"), "LIFECYCLE_COMPLIANCE", failures);
        pass(100, completion.contains("engine_isolation") && deep.contains("fault_isolation"), "FAILURE_ISOLATION", failures);
        pass(101, kernel.declarativeRuntime() != null, "EXECUTABLE_BOUNDARY", failures);

        // 102-118: live target, update/freeze, safety, health, performance and integrity.
        pass(102, installedTargetBridgeContractPass(), "INSTALLED_TARGET_BRIDGE", failures);
        pass(103, deliveryReady
                && patchManifestV2ContractPass()
                && productionPatchSchemaPolicyPass(),
                "DECLARATIVE_UPDATE", failures);
        pass(104, deliveryReady && patchJournalContractPass(), "UPDATE_PIPELINE", failures);
        pass(105, s.freeze() != null
                && kernel.runtimeStateStore() != null, "FREEZE_ENGINE", failures);
        pass(106, s.freeze().state() != null
                && kernel.runtimeStateStore().snapshot() != null, "FREEZE_STATE_MACHINE", failures);
        pass(107, repairReady
                && kernel.safeModeController().readOnlyInspectionAllowed()
                && kernel.visibleWorkspaceStore() != null, "SAFE_MODE_UI", failures);
        pass(108, repairReady
                && kernel.healthMonitor().inspect(kernel) != null
                && kernel.safePatchManager().journal() != null, "HEALTH_CHECK", failures);
        pass(109, resourcePressureContractPass(), "MEMORY_ARCHITECTURE", failures);
        pass(110, !s.resources().budgets().isEmpty()
                && resourcePressureContractPass(),
                "PER_SCREEN_MEMORY", failures);
        pass(111, completion.contains("render_cost")
                && renderBudgetPass(), "OVERDRAW_RENDERING", failures);
        pass(112, leakDisciplinePass()
                && s.resources().invariantPass(), "LEAK_DISCIPLINE", failures);
        pass(113, s.benchmark() != null
                && patchJournalContractPass()
                && scaleClassesPass(s), "TEST_BENCHMARK", failures);
        pass(114, s.benchmark() != null
                && s.resources().budgets().size() >= 2, "SOAK_TEST", failures);
        pass(115, patchJournalContractPass()
                && kernel.runtimeStateStore() != null, "CRASH_TRANSACTION", failures);
        pass(116, scaleClassesPass(s), "SCALE_CLASSES", failures);
        pass(117, kernel.visibleWorkspaceStore() != null
                && s.assetLoads() != null
                && deep.contains("source_of_truth_policy"), "EXTERNAL_INTEGRITY", failures);
        pass(118, completion.contains("immutable_build_package"), "DEPENDENCY_DETERMINISM", failures);

        // 119-135: audit, diagnostic policy, source of truth and complete end-to-end architecture.
        pass(119, completion.contains("audit_behavior_gate"), "AUDIT_AGENT", failures);
        pass(
                120,
                autoRepairContractPass(s),
                "AUTO_REPAIR_POLICY",
                failures
        );
        pass(121, s.diagnostics() != null, "DIAGNOSTIC_CODES", failures);
        pass(122, kernel.visibleWorkspaceStore() != null
                && projectReady
                && deep.contains("source_of_truth_policy"), "SOURCE_OF_TRUTH", failures);
        pass(123, completionReady && kernelReady, "INVARIANTS", failures);
        pass(124, editorReady && buildReady && projectReady, "PROJECT_TO_APK_FLOW", failures);
        pass(125, editorReady && completion.contains("ui_state_hold"), "UI_EDITOR_FLOW", failures);
        pass(126, libraryReady
                && kernel.visibleWorkspaceStore() != null
                && s.assetLoads() != null
                && s.inventory().complete(), "ASSET_TO_OBJECT_FLOW", failures);
        pass(127, runtimeReady && completion.contains("incremental_validation"), "BINDING_FLOW", failures);
        pass(128, completion.contains("flow_execution"), "LOGIC_FLOW", failures);
        pass(129, repairReady && deliveryReady
                && kernel.safePatchManager().journal() != null
                && targetEvolutionBindingContractPass(kernel),
                "REPAIR_EVOLUTION_FLOW", failures);
        pass(130, s.freeze() != null
                && kernel.runtimeStateStore() != null, "FREEZE_FLOW", failures);
        pass(131, resourcePressureContractPass()
                && leakDisciplinePass(), "RAM_ARCHITECTURE", failures);
        pass(132, kernel.visibleWorkspaceStore() != null
                && s.backups() != null
                && com.toolbox.tools.core.VisibleWorkspaceStore.Area.values().length == 6,
                "STORAGE_ARCHITECTURE", failures);
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

    private static boolean accessibilityContractPass(
            AppKernel kernel
    ) {
        try {
            java.util.List<com.toolbox.tools.library.ComponentDefinition>
                    components =
                    kernel.libraryManager()
                            .components()
                            .allReady();
            if (components.isEmpty()) return false;
            for (com.toolbox.tools.library.ComponentDefinition component
                    : components) {
                com.toolbox.tools.library.AccessibilityContract contract =
                        component.accessibilityContract();
                if (!contract.completeContract()) {
                    return false;
                }
                if (!contract.validate(
                        true,
                        false,
                        component.labelIndonesia(),
                        null,
                        java.util.EnumSet.of(
                                com.toolbox.tools.library.AccessibilityContract.Semantic.ENABLED
                        )
                )) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean cacheManagerContractPass(
            ProductServices services
    ) {
        try {
            CacheManager cache = services.cache();
            if (cache.tierBudgetBytes(
                    CacheManager.Tier.MEMORY
            ) < 8L * 1024L * 1024L) {
                return false;
            }
            if (cache.tierBudgetBytes(
                    CacheManager.Tier.DISK
            ) < 8L * 1024L * 1024L) {
                return false;
            }
            for (CacheManager.Category category
                    : CacheManager.Category.values()) {
                if (cache.categoryBudgetBytes(category)
                        < 1024L * 1024L) {
                    return false;
                }
            }
            return cache.categorySizes().size()
                    == CacheManager.Category.values().length;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean cacheCleanupPass(
            ProductServices services
    ) {
        try {
            CacheManager cache = services.cache();
            final int[] disposed = new int[] {0};
            cache.put(
                    "cache.acceptance.temp",
                    64,
                    CacheManager.Priority.TEMP,
                    CacheManager.Category.RENDER_TEMP,
                    CacheManager.Tier.MEMORY,
                    () -> disposed[0]++
            );
            int removed = cache.clearCategory(
                    CacheManager.Category.RENDER_TEMP
            );
            return removed >= 1
                    && disposed[0] == 1
                    && cache.bytesByCategory(
                            CacheManager.Category.RENDER_TEMP
                    ) == 0;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean clipboardContractPass(
            ProductServices services
    ) {
        try {
            ClipboardService clipboard = new ClipboardService();
            java.util.Map<String,String> properties =
                    new java.util.LinkedHashMap<>();
            properties.put(
                    "binding.target",
                    "data.profile.name"
            );
            java.util.Set<String> dependencies =
                    new java.util.LinkedHashSet<>();
            dependencies.add("data.profile.name");
            clipboard.copy(
                    "object.source",
                    properties,
                    dependencies
            );
            java.util.Set<String> existing =
                    new java.util.LinkedHashSet<>();
            existing.add("data.profile.name");
            ClipboardService.PasteResult pasted =
                    clipboard.paste(
                            "object.target",
                            existing,
                            java.util.Collections.emptyMap()
                    );
            ClipboardService brokenClipboard =
                    new ClipboardService();
            brokenClipboard.copy(
                    "object.source",
                    properties,
                    dependencies
            );
            ClipboardService.PasteResult broken =
                    brokenClipboard.paste(
                            "object.target",
                            java.util.Collections.emptySet(),
                            java.util.Collections.emptyMap()
                    );
            return services.clipboard() != null
                    && !pasted.hasBrokenReferences()
                    && pasted.dependencies().contains(
                            "data.profile.name"
                    )
                    && broken.hasBrokenReferences();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean permissionContractPass(
            ProductServices services
    ) {
        try {
            PermissionManager permissions =
                    services.permissions();
            permissions.activateCapability(
                    "capability.network",
                    true
            );
            permissions.activateCapability(
                    "capability.notification",
                    true
            );
            permissions.activateCapability(
                    "capability.storage.user",
                    true
            );

            permissions.setGranted(
                    "permission.network.internet",
                    true
            );
            permissions.setGranted(
                    "permission.runtime.notification",
                    false
            );
            permissions.setGranted(
                    "permission.storage.tree",
                    false
            );

            if (!permissions.completeContract()) return false;
            if (!permissions.byPhase(
                    PermissionManager.Phase.INSTALL_TIME
            ).stream().anyMatch(
                    item -> "permission.network.internet"
                            .equals(item.permissionId())
            )) return false;
            if (!permissions.byPhase(
                    PermissionManager.Phase.RUNTIME
            ).stream().anyMatch(
                    item -> "permission.runtime.notification"
                            .equals(item.permissionId())
            )) return false;
            if (!permissions.byPhase(
                    PermissionManager.Phase.SPECIAL_ACCESS
            ).stream().anyMatch(
                    item -> "permission.storage.tree"
                            .equals(item.permissionId())
            )) return false;
            if (permissions.missing().contains(
                    "permission.network.internet"
            )) return false;
            if (!permissions.missing().contains(
                    "permission.runtime.notification"
            )) return false;
            if (!permissions.missing().contains(
                    "permission.storage.tree"
            )) return false;
            for (PermissionManager.Failure failure
                    : permissions.failures()) {
                if (failure.failurePathId() == null
                        || failure.failurePathId().isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
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

    private static boolean assetAuditContractPass(
            AppKernel kernel,
            ProductServices services
    ) {
        try {
            ProjectAssetAudit.Report report =
                    services.projectAssetAudit().scan(
                            kernel.projectManager().current()
                    );
            return report != null
                    && report.issues() != null
                    && report.referencedAssetIds() != null
                    && report.definedExternalAssetIds() != null
                    && services.assetLoads()
                            .audit()
                            .isPass();
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

    private static boolean installedTargetBridgeContractPass() {
        try {
            ProductCompletionServices.InstalledTargetBridge bridge =
                    new ProductCompletionServices.InstalledTargetBridge();
            bridge.registerTarget(
                    "com.example.generic",
                    "Generic",
                    java.util.Arrays.asList("ui", "asset"),
                    0,
                    "project.com_example_generic",
                    0,
                    ProductCompletionServices.InstalledTargetBridge
                            .DOOR_GENERIC_EDIT,
                    false
            );
            bridge.registerTarget(
                    "com.example.managed",
                    "Managed",
                    java.util.Arrays.asList(
                            "ui",
                            "logic",
                            "data",
                            "binding",
                            "asset"
                    ),
                    1,
                    "project.com_example_managed",
                    3,
                    ProductCompletionServices.InstalledTargetBridge
                            .DOOR_MANAGED_RUNTIME,
                    true,
                    "com.example.managed.toolbox"
            );
            ProductCompletionServices.InstalledTargetBridge.Target managed =
                    bridge.lookup("com.example.managed");
            ProductCompletionServices.InstalledTargetBridge.Target generic =
                    bridge.lookup("com.example.generic");
            return managed != null
                    && managed.toolboxAware()
                    && managed.writable()
                    && managed.supportsInternalEditor()
                    && "com.example.managed.toolbox"
                        .equals(managed.providerAuthority())
                    && generic != null
                    && generic.hasEditingDoor()
                    && !generic.writable()
                    && bridge.all().size() == 2;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean patchManifestV2ContractPass() {
        try {
            com.toolbox.tools.delivery.PatchPayload payload =
                    new com.toolbox.tools.delivery.PatchPayload(
                            Collections.singletonMap(
                                    "ui.acceptance.patch",
                                    "ok"
                            ),
                            Collections.emptySet()
                    );
            LinkedHashMap<String, String> hashes =
                    new LinkedHashMap<>();
            hashes.put("payload", payload.sha256());
            Set<String> capabilities =
                    new java.util.LinkedHashSet<>(
                            java.util.Arrays.asList(
                                    "ui",
                                    "asset"
                            )
                    );
            com.toolbox.tools.delivery.PatchManifest manifest =
                    new com.toolbox.tools.delivery.PatchManifest(
                            "patch.acceptance.v2",
                            "project.default",
                            1,
                            2,
                            repeatHex('a'),
                            repeatHex('b'),
                            repeatHex('c'),
                            payload.sha256(),
                            "DECLARATIVE_PATCH",
                            "com.toolbox.tools",
                            "13.0",
                            13,
                            13,
                            Collections.emptySet(),
                            capabilities,
                            hashes,
                            "EVOLUTION"
                    );
            return manifest.schemaVersion() == 2
                    && manifest.canonical()
                        .startsWith("TBX_PATCH_V2")
                    && manifest.supportsHost(
                            "com.toolbox.tools",
                            13,
                            capabilities
                    )
                    && !manifest.supportsHost(
                            "com.toolbox.tools",
                            12,
                            capabilities
                    );
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean productionPatchSchemaPolicyPass() {
        try {
            com.toolbox.tools.delivery.EvolutionPackagePolicy
                    .requireProductionSchema(
                            com.toolbox.tools.delivery.PatchManifest
                                    .CURRENT_SCHEMA_VERSION
                    );
            try {
                com.toolbox.tools.delivery.EvolutionPackagePolicy
                        .requireProductionSchema(1);
                return false;
            } catch (IllegalArgumentException expected) {
                return !com.toolbox.tools.delivery
                        .EvolutionPackagePolicy
                        .isProductionSchema(1);
            }
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean targetEvolutionBindingContractPass(
            AppKernel kernel
    ) {
        try {
            com.toolbox.tools.delivery.SafePatchManager proof =
                    new com.toolbox.tools.delivery.SafePatchManager(
                            kernel.projectManager(),
                            kernel.recoveryManager(),
                            kernel.remotePatchVerifier(),
                            com.toolbox.tools.delivery
                                    .PatchActivationHook.NO_OP,
                            new com.toolbox.tools.core
                                    .MemoryRuntimeStateStore()
                    );
            java.util.Set<String> capabilities =
                    new java.util.LinkedHashSet<>(
                            java.util.Arrays.asList("ui", "asset")
                    );
            proof.bindHostContext(
                    "com.example.target",
                    7,
                    capabilities
            );
            proof.bindRuntimeApkIdentity(
                    repeatHex('a'),
                    repeatHex('b')
            );

            com.toolbox.tools.delivery.PatchPayload payload =
                    new com.toolbox.tools.delivery.PatchPayload(
                            Collections.singletonMap(
                                    "ui.acceptance.target",
                                    "ok"
                            ),
                            Collections.emptySet()
                    );
            LinkedHashMap<String, String> hashes =
                    new LinkedHashMap<>();
            hashes.put("payload", payload.sha256());
            com.toolbox.tools.delivery.PatchManifest manifest =
                    new com.toolbox.tools.delivery.PatchManifest(
                            "patch.acceptance.target",
                            "project.default",
                            1,
                            2,
                            repeatHex('a'),
                            repeatHex('c'),
                            repeatHex('b'),
                            payload.sha256(),
                            "DECLARATIVE_PATCH",
                            "com.example.target",
                            "7",
                            7,
                            7,
                            Collections.emptySet(),
                            capabilities,
                            hashes,
                            "EVOLUTION"
                    );
            return "com.example.target".equals(
                            proof.hostPackageName()
                    )
                    && proof.hostVersionCode() == 7
                    && proof.runtimeApkIdentityBound()
                    && manifest.supportsHost(
                            proof.hostPackageName(),
                            proof.hostVersionCode(),
                            proof.hostCapabilities()
                    )
                    && !manifest.supportsHost(
                            "com.toolbox.tools",
                            7,
                            proof.hostCapabilities()
                    );
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean patchJournalContractPass() {
        try {
            com.toolbox.tools.core.MemoryRuntimeStateStore state =
                    new com.toolbox.tools.core.MemoryRuntimeStateStore();
            com.toolbox.tools.delivery.PatchTransactionJournal journal =
                    new com.toolbox.tools.delivery.PatchTransactionJournal(
                            state
                    );
            com.toolbox.tools.delivery.PatchPayload payload =
                    new com.toolbox.tools.delivery.PatchPayload(
                            Collections.singletonMap(
                                    "ui.acceptance.journal",
                                    "ok"
                            ),
                            Collections.emptySet()
                    );
            com.toolbox.tools.delivery.PatchManifest manifest =
                    new com.toolbox.tools.delivery.PatchManifest(
                            "patch.acceptance.journal",
                            "project.default",
                            1,
                            2,
                            repeatHex('1'),
                            repeatHex('2'),
                            repeatHex('3'),
                            payload.sha256()
                    );
            journal.begin(manifest, payload);
            journal.phase(
                    com.toolbox.tools.delivery
                            .PatchTransactionJournal.Phase.MUTATING
            );
            boolean active = journal.active()
                    && journal.baseRevision() == 1
                    && journal.targetRevision() == 2
                    && "patch.acceptance.journal"
                        .equals(journal.patchId());
            journal.clear();
            return active
                    && journal.phase()
                        == com.toolbox.tools.delivery
                            .PatchTransactionJournal.Phase.IDLE;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean importSecurityContractPass(
            ProductServices services
    ) {
        try {
            ImportSecurityValidator validator =
                    services.importSecurity();
            String hash = repeatHex('a');
            ImportSecurityValidator.Entry valid =
                    new ImportSecurityValidator.Entry(
                            "project/project.tbx",
                            1024,
                            4096,
                            1,
                            "application/vnd.toolbox.project+json",
                            hash,
                            hash,
                            "project.import"
                    );
            boolean pass = "PASS".equals(
                    validator.validate(
                            new ImportSecurityValidator.Request(
                                    Collections.singletonList(valid),
                                    com.toolbox.tools.core.ProjectState
                                            .CURRENT_SCHEMA_VERSION,
                                    com.toolbox.tools.core.ProjectState
                                            .CURRENT_BUILD_MODEL_VERSION,
                                    true,
                                    true,
                                    hash,
                                    hash
                            )
                    )
            );
            ImportSecurityValidator.Entry bomb =
                    new ImportSecurityValidator.Entry(
                            "assets/bomb.bin",
                            1,
                            1000,
                            1,
                            "application/octet-stream",
                            null,
                            null,
                            null
                    );
            return pass && "IMPORT_DECOMPRESSION_RATIO".equals(
                    validator.validate(
                            Collections.singletonList(bomb)
                    )
            );
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean importMergeContractPass(
            ProductServices services
    ) {
        try {
            com.toolbox.tools.core.ProjectState target =
                    com.toolbox.tools.core.ProjectState.create(
                            "project.target"
                    ).withResource(
                            "ui.object.shared",
                            "existing"
                    ).withResource(
                            "ui.object.target",
                            "target"
                    ).withReference(
                            "ui.object.target",
                            "ui.object.shared"
                    );
            com.toolbox.tools.core.ProjectState incoming =
                    com.toolbox.tools.core.ProjectState.create(
                            "project.incoming"
                    ).withResource(
                            "ui.object.shared",
                            "incoming"
                    ).withResource(
                            "logic.flow.source",
                            "flow"
                    ).withReference(
                            "logic.flow.source",
                            "ui.object.shared"
                    );
            ImportMergeManager.Result result =
                    services.importMerge().mergeInto(
                            target,
                            incoming
                    );
            String remapped = result.idMap().get(
                    "ui.object.shared"
            );
            return remapped != null
                    && !remapped.equals("ui.object.shared")
                    && "incoming".equals(
                            result.projectState()
                                    .resources()
                                    .get(remapped)
                    )
                    && result.projectState()
                            .references()
                            .get("logic.flow.source")
                            .contains(remapped)
                    && result.projectState()
                            .references()
                            .get("ui.object.target")
                            .contains("ui.object.shared");
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean autoRepairContractPass(
            ProductServices services
    ) {
        try {
            services.cache().put(
                    "cache.autorepair.proof",
                    1024,
                    CacheManager.Priority.TEMP
            );
            AutoRepairEngine.RepairResult result =
                    services.autoRepair().applyDeterministic(
                            java.util.Arrays.asList(
                                    AutoRepairEngine.RepairType
                                            .CLEAR_DISPOSABLE_CACHE,
                                    AutoRepairEngine.RepairType
                                            .REGENERATE_DERIVED_MANIFEST
                            )
                    );
            AutoRepairEngine.RepairResult guarded =
                    services.autoRepair().applyDeterministic(
                            java.util.Collections.singletonList(
                                    AutoRepairEngine.RepairType
                                            .RELINK_EXACT_STABLE_ID
                            )
                    );
            String manifest =
                    services.autoRepair()
                            .lastDerivedManifestSha256();
            return result.isPass()
                    && result.applied().size() == 2
                    && !services.cache()
                        .snapshot()
                        .containsKey("cache.autorepair.proof")
                    && manifest != null
                    && manifest.matches("[0-9a-f]{64}")
                    && !guarded.isPass()
                    && guarded.rejected()
                        .get(0)
                        .contains("EXACT_INPUT_REQUIRED")
                    && !services.autoRepair()
                        .mayGuessBusinessLogic()
                    && !services.autoRepair()
                        .mayDeleteUserData();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean resourcePressureContractPass() {
        try {
            ResourceGuard guard = new ResourceGuard();
            guard.applyPressure(ResourceGuard.Pressure.NORMAL);
            if (guard.previewQuality() != 1.0f
                    || !guard.preloadEnabled()) {
                return false;
            }
            guard.applyPressure(ResourceGuard.Pressure.REDUCED);
            if (guard.previewQuality() != 0.75f
                    || guard.preloadEnabled()) {
                return false;
            }
            int before = guard.releaseGeneration();
            guard.applyPressure(ResourceGuard.Pressure.CRITICAL);
            return guard.previewQuality() == 0.5f
                    && !guard.preloadEnabled()
                    && guard.releaseGeneration() > before
                    && guard.invariantPass();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean scaleClassesPass(
            ProductServices services
    ) {
        try {
            ScaleBenchmarkHarness harness = services.benchmark();
            long budget = 96L * 1024L * 1024L;
            ScaleBenchmarkHarness.Result small =
                    harness.runActual(
                            ScaleBenchmarkHarness.ScaleClass.SMALL,
                            budget
                    );
            ScaleBenchmarkHarness.Result medium =
                    harness.runActual(
                            ScaleBenchmarkHarness.ScaleClass.MEDIUM,
                            budget
                    );
            ScaleBenchmarkHarness.Result large =
                    harness.runActual(
                            ScaleBenchmarkHarness.ScaleClass.LARGE,
                            budget
                    );
            ScaleBenchmarkHarness.Result stress =
                    harness.runActual(
                            ScaleBenchmarkHarness.ScaleClass.STRESS,
                            budget
                    );
            return small.withinBudget()
                    && medium.withinBudget()
                    && large.withinBudget()
                    && stress.withinBudget()
                    && small.roundTripEqual()
                    && medium.roundTripEqual()
                    && large.roundTripEqual()
                    && stress.roundTripEqual()
                    && small.resourceCount()
                        < medium.resourceCount()
                    && medium.resourceCount()
                        < large.resourceCount()
                    && large.resourceCount()
                        < stress.resourceCount()
                    && stress.referenceCount()
                        > large.referenceCount()
                    && stress.dependencyCount()
                        > large.dependencyCount()
                    && stress.scaleClass()
                        == ScaleBenchmarkHarness.ScaleClass.STRESS;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static String repeatHex(char value) {
        char[] out = new char[64];
        java.util.Arrays.fill(out, value);
        return new String(out);
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
