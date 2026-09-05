package com.toolbox.tools.product;

import com.toolbox.tools.authoring.AuthoringSection;
import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.delivery.PatchPayload;
import com.toolbox.tools.library.AssetDescriptor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

public final class FullProductArchitectureTest {
    @Test
    public void seluruhRancanganProdukPenuhTerpasangPadaKernel() throws Exception {
        AppKernel kernel = AppKernel.createDefault();

        FullProductVerifier.Result result =
                new FullProductVerifier().verify(kernel);

        assertTrue(result.errors().toString(), result.isPass());
        assertEquals(
                ProductCapability.values().length,
                result.available().size()
        );
        assertEquals(6, kernel.toolRegistry().size());
        assertEquals(6, kernel.engineManager().snapshot().size());
        assertTrue(kernel.productEngines().semuaSiap());
        assertTrue(kernel.productServices().isReady());
        assertEquals(
                "id",
                kernel.configStore().get("bahasaDefault", "")
        );
        assertEquals(
                "produk-penuh-v13-maksimal",
                kernel.configStore().get("tahap", "")
        );
        assertTrue(
                kernel.libraryManager().components().allReady().size() >= 18
        );
        assertTrue(
                kernel.libraryManager().templates().allReady().size() >= 4
        );
        assertTrue(
                kernel.libraryManager().assets().allReady().size() >= 5
        );

        for (AssetDescriptor asset
                : kernel.libraryManager().assets().allReady()) {
            assertTrue(
                    asset.assetId(),
                    kernel.assetStore().originalExists(asset)
            );
        }
    }

    @Test
    public void limaToolMemakaiLifecycleSatuFungsiBeratAktif() {
        AppKernel kernel = AppKernel.createDefault();
        ToolLifecycleManager lifecycle =
                kernel.productServices().toolLifecycle();

        assertEquals(1, lifecycle.activeCount());
        assertEquals(
                ToolLifecycleManager.State.ACTIVE,
                lifecycle.snapshot().get("tool.ui")
        );

        lifecycle.activate("tool.logic");

        assertEquals(1, lifecycle.activeCount());
        assertEquals(
                ToolLifecycleManager.State.COLD,
                lifecycle.snapshot().get("tool.ui")
        );
        assertEquals(
                ToolLifecycleManager.State.ACTIVE,
                lifecycle.snapshot().get("tool.logic")
        );
    }

    @Test
    public void graphLayoutStateDanAnimasiMemilikiSemantikNyata() {
        AppKernel kernel = AppKernel.createDefault();
        ProductServices services = kernel.productServices();

        assertTrue(
                services.projectGraph()
                        .impactOf("screen.detail")
                        .contains("object.home.primary")
        );

        VisualLayoutEngine.Node before =
                services.visualLayout()
                        .snapshot()
                        .get("object.home.primary");
        assertNotNull(before);

        services.visualLayout().move(
                "object.home.primary",
                33,
                55,
                8
        );
        VisualLayoutEngine.Node moved =
                services.visualLayout()
                        .snapshot()
                        .get("object.home.primary");
        assertEquals(32f, moved.x(), 0.01f);
        assertEquals(56f, moved.y(), 0.01f);
        assertSame(
                moved,
                services.visualLayout().hitTest(40, 60)
        );

        assertEquals(
                "#4CC9FF",
                services.stateVariants()
                        .resolve(
                                "object.home.primary",
                                "state.pressed"
                        )
                        .get("property.color")
        );
        assertFalse(services.animations().all().isEmpty());
    }

    @Test
    public void sandboxImportMergeDanBenchmarkFailClosed() {
        AppKernel kernel = AppKernel.createDefault();
        ProductServices services = kernel.productServices();

        assertFalse(
                services.previewSandbox().mayExecuteInPreview(
                        PreviewSandbox.SideEffect.NETWORK
                )
        );
        assertEquals(
                "DISIMULASIKAN_OLEH_SAFETY_GATE",
                services.previewSandbox().simulate(
                        PreviewSandbox.SideEffect.PAYMENT
                )
        );

        ImportMergeManager.Result merged =
                services.importMerge().mergeInto(
                        "project.default",
                        Arrays.asList("screen.home", "screen.new"),
                        Collections.singletonList("screen.home")
                );
        assertNotEquals(
                "screen.home",
                merged.idMap().get("screen.home")
        );
        assertEquals(
                "screen.new",
                merged.idMap().get("screen.new")
        );

        ScaleBenchmarkHarness.Result benchmark =
                services.benchmark().estimate(
                        ScaleBenchmarkHarness.ScaleClass.LARGE,
                        10_000,
                        120,
                        8L * 1024L * 1024L,
                        64L * 1024L * 1024L
                );
        assertTrue(benchmark.withinBudget());
        assertEquals(120, benchmark.visibleNodes());
    }

    @Test
    public void runtimeDeklaratifMencakupLimaToolTanpaRebuild() {
        AppKernel kernel = AppKernel.createDefault();
        DeclarativeProjectRuntime runtime = kernel.declarativeRuntime();

        assertTrue(runtime.supportsWithoutRebuild("ui.screen.home.title"));
        assertTrue(runtime.supportsWithoutRebuild("logic.flow.home.rule"));
        assertTrue(runtime.supportsWithoutRebuild("data.items.mock"));
        assertTrue(runtime.supportsWithoutRebuild("binding.home.title.mode"));
        assertTrue(runtime.supportsWithoutRebuild("asset.theme.active"));
        assertFalse(runtime.supportsWithoutRebuild("native.new.engine"));

        PatchPayload payload = new PatchPayload(
                new java.util.LinkedHashMap<String, String>() {{
                    put("ui.screen.home.title", "Judul Baru");
                    put("logic.flow.home.rule", "rule.v2");
                    put("data.items.mock", "data.v2");
                    put("binding.home.title.mode", "ONE_WAY");
                    put("asset.theme.active", "asset.theme.dark.neon");
                }},
                Collections.emptySet()
        );

        Set<AuthoringSection> affected = runtime.validatePatch(payload);
        assertEquals(
                new LinkedHashSet<>(Arrays.asList(
                        AuthoringSection.UI,
                        AuthoringSection.LOGIC,
                        AuthoringSection.DATA,
                        AuthoringSection.BINDING,
                        AuthoringSection.ASSET
                )),
                affected
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void runtimeDeklaratifMenolakKapabilitasExecutableBaru() {
        new DeclarativeProjectRuntime(
                com.toolbox.tools.core.ProjectState.create("project.default")
        ).validatePatch(
                new PatchPayload(
                        Collections.singletonMap(
                                "native.engine.new",
                                "binary"
                        ),
                        Collections.emptySet()
                )
        );
    }

    @Test
    public void autoRepairTidakMenebakBusinessLogicAtauMenghapusData() {
        AutoRepairEngine repair = new AutoRepairEngine();
        assertFalse(repair.mayGuessBusinessLogic());
        assertFalse(repair.mayDeleteUserData());
        assertTrue(
                repair.applyDeterministic(Arrays.asList(
                        AutoRepairEngine.RepairType.REBUILD_DERIVED_INDEX,
                        AutoRepairEngine.RepairType.CLEAR_DISPOSABLE_CACHE
                )).isPass()
        );
    }
}
