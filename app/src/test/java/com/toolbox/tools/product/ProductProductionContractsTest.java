package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.IncrementalResourceValidator;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class ProductProductionContractsTest {
    @Test public void repositoryInventoryIsClosedAndAuthoritative() {
        RepositoryInventory inventory=new RepositoryInventory();
        assertTrue(inventory.complete());
        assertEquals(RepositoryInventory.Type.PERMISSION,inventory.require("permission.storage.tree").type());
        assertEquals("SafProjectStore",inventory.require("implementation.project.store").implementation());
    }

    @Test public void inputRouterHasCaptureTargetBubbleAndFocus() {
        InputRouter r=new InputRouter();
        r.register("screen.home",null);r.register("container.home","screen.home");r.register("object.button","container.home");
        r.setFocusOrder(Arrays.asList("object.button"));
        InputRouter.Dispatch d=r.dispatch("object.button",InputRouter.Event.TAP,InputRouter.Propagation.CONTINUE);
        assertEquals(Arrays.asList("screen.home","container.home"),d.capture());
        assertEquals("object.button",d.target());
        assertEquals(Arrays.asList("container.home","screen.home"),d.bubble());
        assertEquals("object.button",r.nextFocus("object.button"));
    }

    @Test public void conditionalPropertiesArePureAndDeterministic() {
        ConditionalPropertyEngine e=new ConditionalPropertyEngine();
        Map<String,String> c=new LinkedHashMap<>();c.put("data.valid","true");c.put("user.role","admin");
        assertTrue(e.evaluate("data.valid && user.role == admin",c));
        assertFalse(e.evaluate("data.valid && user.role == guest",c));
        assertTrue(e.evaluate("user.role == guest || data.valid",c));
    }

    @Test public void visualLayoutSupportsGuidesLocksLayersInsetsResponsiveAndGroups() {
        VisualLayoutEngine e=new VisualLayoutEngine();
        e.add(new VisualLayoutEngine.Node("root",null,0,0,360,640,0,false,VisualLayoutEngine.PointerBehavior.AUTO));
        e.add(new VisualLayoutEngine.Node("a","root",17,19,40,20,1,false,VisualLayoutEngine.PointerBehavior.AUTO));
        e.add(new VisualLayoutEngine.Node("b","root",101,30,60,30,2,false,VisualLayoutEngine.PointerBehavior.AUTO));
        e.add(new VisualLayoutEngine.Node("c","root",220,40,50,25,3,false,VisualLayoutEngine.PointerBehavior.AUTO));
        e.addGuide(new VisualLayoutEngine.Guide("guide.x",VisualLayoutEngine.GuideAxis.X,24));
        e.move("a",22,22,8);
        assertEquals(24f,e.snapshot().get("a").x(),0.01f);
        e.alignLeft(Arrays.asList("a","b","c"));
        e.equalSize(Arrays.asList("a","b","c"));
        assertEquals(e.snapshot().get("a").width(),e.snapshot().get("c").width(),0.01f);
        e.setLayer("c",VisualLayoutEngine.Layer.OVERLAY);
        assertEquals("c",e.hitTest(24,40).id());
        e.setSafeInsets(new VisualLayoutEngine.Insets(8,24,8,16));
        e.clampToSafeArea("a",0,0,360,640);
        assertTrue(e.snapshot().get("a").x()>=8);
        e.setResponsiveOverride("screen.home",VisualLayoutEngine.Orientation.LANDSCAPE,"property.width",196);
        assertEquals(Float.valueOf(196),e.responsiveOverride("screen.home",VisualLayoutEngine.Orientation.LANDSCAPE).get("property.width"));
        e.setLocks("a",EnumSet.of(VisualLayoutEngine.LockAspect.POSITION));
        try { e.move("a",100,100,8); fail(); } catch (IllegalStateException expected) {}
        e.setViewport(1.5f,12,18);
        assertEquals(1.5f,e.zoom(),0.01f);
        assertEquals(Arrays.asList("root","c"),e.pathToRoot("c"));
    }

    @Test public void stateLayersHaveDeterministicPrecedence() {
        StateVariantEngine e=new StateVariantEngine();
        e.setNormal("object.a","property.color","base");
        e.setLayerOverride("object.a",StateVariantEngine.Layer.ORIENTATION,"orientation.landscape","property.color","orientation");
        e.setLayerOverride("object.a",StateVariantEngine.Layer.THEME,"theme.dark","property.color","theme");
        e.setLayerOverride("object.a",StateVariantEngine.Layer.DATA,"data.error","property.color","data");
        e.setStateOverride("object.a","state.pressed","property.color","state");
        assertEquals("state",e.resolve("object.a","state.pressed","orientation.landscape","theme.dark","data.error").get("property.color"));
    }

    @Test public void animationTimelineSupportsSequenceAndParallelContracts() {
        AnimationEngine e=new AnimationEngine();
        e.register(new AnimationEngine.Animation("a",AnimationEngine.Kind.FADE,"event.a",100,0,AnimationEngine.Easing.LINEAR));
        e.register(new AnimationEngine.Animation("b",AnimationEngine.Kind.SCALE,"event.b",200,10,AnimationEngine.Easing.EASE_OUT));
        e.registerGroup(new AnimationEngine.Group("g.seq",AnimationEngine.GroupMode.SEQUENCE,Arrays.asList("a","b")));
        e.registerGroup(new AnimationEngine.Group("g.par",AnimationEngine.GroupMode.PARALLEL,Arrays.asList("a","b")));
        assertEquals(310,e.groupDuration("g.seq"));
        assertEquals(210,e.groupDuration("g.par"));
    }

    @Test public void localizationCoversPluralCurrencyDateAndRtl() {
        LocalizationManager l=new LocalizationManager();
        l.putPlural("items","id","item","item");
        assertEquals("2 item",l.resolvePlural("items","id",2));
        assertNotNull(l.formatCurrency(12500,"IDR","id-ID"));
        assertNotNull(l.formatDate(0,"id-ID"));
        assertTrue(l.isRtl("ar"));
        assertFalse(l.isRtl("id"));
    }

    @Test public void dataProviderEcosystemAndViewportAreProductionReady() {
        DataProviderRegistry providers = new DataProviderRegistry();
        assertTrue(providers.complete());
        DataProviderRegistry.Window window = providers.window(
                1000,
                120,
                20,
                10
        );
        assertEquals(110, window.first());
        assertEquals(149, window.last());
        assertEquals(40, window.materializedCount());
        assertTrue(
                providers.require("provider.database")
                        .capabilities()
                        .contains(
                                DataProviderRegistry.Capability.TRANSACTION
                        )
        );
    }

    @Test public void assetLoadPlanningCoversPreviewAndStreaming() {
        AssetLoadManager assets = new AssetLoadManager();
        String imageSha =
                "1111111111111111111111111111111111111111111111111111111111111111";
        String videoSha =
                "2222222222222222222222222222222222222222222222222222222222222222";
        assets.register(
                "asset.image.test",
                AssetLoadManager.Kind.IMAGE,
                4096,
                imageSha
        );
        assets.register(
                "asset.video.test",
                AssetLoadManager.Kind.VIDEO,
                1024 * 1024,
                videoSha
        );
        assets.reference("asset.image.test");
        assets.reference("asset.video.test");
        AssetLoadManager.LoadPlan image = assets.plan(
                "asset.image.test",
                1080,
                1920,
                true
        );
        AssetLoadManager.LoadPlan video = assets.plan(
                "asset.video.test",
                1080,
                1920,
                true
        );
        assertTrue(image.thumbnailFirst());
        assertFalse(image.streaming());
        assertTrue(video.streaming());
        assertEquals(512 * 1024, video.chunkBytes());
        assertTrue(assets.audit().isPass());
    }

    @Test public void incrementalValidatorRejectsInvalidDeltaBeforeMutation() {
        IncrementalResourceValidator validator =
                new IncrementalResourceValidator();
        AppKernel kernel = AppKernel.createDefault();
        Map<String,String> good = new LinkedHashMap<>();
        good.put("ui.object.home.primary.opacity", "0.5");
        assertTrue(
                validator.validate(
                        kernel.projectManager().current(),
                        good,
                        Collections.emptySet()
                ).isPass()
        );
        Map<String,String> bad = new LinkedHashMap<>();
        bad.put("ui.object.home.primary.opacity", "3.0");
        assertFalse(
                validator.validate(
                        kernel.projectManager().current(),
                        bad,
                        Collections.emptySet()
                ).isPass()
        );
    }

    @Test public void renderDiagnosticsFailClosedOnBudget() {
        RenderDiagnostics monitor = new RenderDiagnostics();
        monitor.record("screen.home", 80, 2, 2, 16);
        assertTrue(monitor.allWithinBudget());
        monitor.record("screen.detail", 300, 1, 0, 16);
        assertFalse(monitor.allWithinBudget());
    }

    @Test public void toolLifecycleRunsReleaseHooksOnSwitch() {
        ToolLifecycleManager lifecycle =
                new ToolLifecycleManager();
        lifecycle.register("tool.a");
        lifecycle.register("tool.b");
        final int[] released = new int[] {0};
        lifecycle.registerReleaseHook(
                "tool.a",
                () -> released[0]++
        );
        lifecycle.activate("tool.a");
        lifecycle.activate("tool.b");
        assertEquals(1, released[0]);
        assertEquals(1, lifecycle.releaseCount("tool.a"));
        assertEquals(1, lifecycle.activeCount());
    }

    @Test public void projectManagerRejectsInvalidIncrementalMutation() {
        AppKernel kernel = AppKernel.createDefault();
        Map<String,String> bad = new LinkedHashMap<>();
        bad.put("ui.object.home.primary.opacity", "4.0");
        try {
            kernel.projectManager().applyResourceTransaction(
                    bad,
                    Collections.emptySet()
            );
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(
                    expected.getMessage().contains(
                            "RESOURCE_OPACITY_RANGE"
                    )
            );
        }
    }

    @Test public void conditionalPropertySupportsBooleanLiterals() {
        ConditionalPropertyEngine engine =
                new ConditionalPropertyEngine();
        Map<String,String> context = new LinkedHashMap<>();
        context.put("data.valid", "true");
        assertTrue(engine.evaluate("true", context));
        assertFalse(engine.evaluate("false", context));
        assertTrue(engine.evaluate("data.valid && true", context));
        assertFalse(engine.evaluate("data.valid && false", context));
    }

    @Test public void visualStateHoldSupportsEmptyAndNonEmptySurfaceSets() {
        ProductCompletionServices.UiStateHoldManager hold =
                new ProductCompletionServices.UiStateHoldManager();

        ProductCompletionServices.UiStateHoldManager.Snapshot empty =
                hold.enterEdit("screen.home");
        assertTrue(empty.surfaces().isEmpty());
        ProductCompletionServices.UiStateHoldManager.Snapshot restoredEmpty =
                hold.exitEdit("screen.home");
        assertTrue(restoredEmpty.surfaces().isEmpty());

        hold.open(
                "screen.home",
                ProductCompletionServices.UiStateHoldManager.Surface.DIALOG
        );
        ProductCompletionServices.UiStateHoldManager.Snapshot nonEmpty =
                hold.enterEdit("screen.home");
        assertTrue(
                nonEmpty.surfaces().contains(
                        ProductCompletionServices.UiStateHoldManager.Surface.DIALOG
                )
        );
        ProductCompletionServices.UiStateHoldManager.Snapshot restored =
                hold.exitEdit("screen.home");
        assertEquals(nonEmpty, restored);
    }

    @Test public void cacheManagerSeparatesCategoryTierAndDisposesFiles() {
        CacheManager cache = new CacheManager();
        final int[] disposed = new int[] {0};

        cache.put(
                "preview.asset.a",
                1024,
                CacheManager.Priority.COLD,
                CacheManager.Category.PREVIEW,
                CacheManager.Tier.DISK,
                () -> disposed[0]++
        );
        cache.put(
                "thumb.asset.a",
                512,
                CacheManager.Priority.WARM,
                CacheManager.Category.THUMBNAIL,
                CacheManager.Tier.MEMORY
        );

        assertEquals(
                1024,
                cache.bytesByCategory(
                        CacheManager.Category.PREVIEW
                )
        );
        assertEquals(
                512,
                cache.totalBytes(CacheManager.Tier.MEMORY)
        );
        assertEquals(
                1024,
                cache.totalBytes(CacheManager.Tier.DISK)
        );

        assertEquals(
                1,
                cache.clearCategory(
                        CacheManager.Category.PREVIEW
                )
        );
        assertEquals(1, disposed[0]);
        assertEquals(
                0,
                cache.bytesByCategory(
                        CacheManager.Category.PREVIEW
                )
        );
        assertEquals(512, cache.totalBytes());
    }

    @Test public void freezeMaintainsFrozenBaseAndRecoverySlots() throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        FreezeEngine freeze = kernel.productServices().freeze();
        freeze.freeze();
        long initial = freeze.frozenRevision();
        assertTrue(initial > 0);
        assertEquals(initial, freeze.recoveryARevision());
        assertTrue(freeze.hasFrozenBase());
        assertEquals(
                FreezeEngine.SaveMode.CHECKPOINT,
                freeze.saveMode()
        );

        Map<String,String> change = new LinkedHashMap<>();
        change.put("ui.screen.home.title", "Working");
        kernel.projectManager().applyResourceTransaction(
                change,
                Collections.emptySet()
        );
        freeze.commit();
        assertTrue(freeze.frozenRevision() >= initial);
        assertEquals(initial, freeze.recoveryBRevision());

        Map<String,String> second = new LinkedHashMap<>();
        second.put("ui.screen.home.title", "Temporary");
        kernel.projectManager().applyResourceTransaction(
                second,
                Collections.emptySet()
        );
        freeze.recover();
        assertEquals(FreezeEngine.State.FROZEN, freeze.state());
        assertEquals(
                FreezeEngine.SaveMode.RECOVERY,
                freeze.saveMode()
        );
        freeze.thaw();
        assertEquals(FreezeEngine.State.NORMAL, freeze.state());
        assertEquals(
                FreezeEngine.SaveMode.NORMAL,
                freeze.saveMode()
        );
    }

    @Test public void resourceGuardHasRealPerScreenBudgetsAndLeakTrend() {
        ResourceGuard r=new ResourceGuard();
        r.enterScreen("screen.home");
        assertEquals(ResourceGuard.Pressure.NORMAL,r.sample("screen.home",20L*1024*1024,40,1));
        for(int i=0;i<5;i++)r.sample("screen.home",(20L+i*3L)*1024*1024,40,1);
        assertTrue(r.leakTrend("screen.home"));
        assertTrue(r.invariantPass());
    }
}
