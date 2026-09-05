package com.toolbox.tools;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.AppState;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.android.ManagedAppProjectStore;
import com.toolbox.tools.android.InstalledApkIdentity;
import com.toolbox.tools.android.ToolboxAwareTargetDiscovery;
import com.toolbox.tools.product.ProductCompletionServices;
import com.toolbox.tools.delivery.PatchManifest;
import com.toolbox.tools.delivery.PatchPayload;
import com.toolbox.tools.delivery.PatchTransactionJournal;
import com.toolbox.tools.product.FreezeEngine;
import com.toolbox.tools.product.ResourceGuard;
import com.toolbox.tools.ui.AndroidAssetRenderer;
import com.toolbox.tools.ui.UiCanvasView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ProductRuntimeInstrumentedTest {
    @Test
    public void activityLaunchesRealKernelAndShell() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.kernelForTest());
                assertEquals(
                        AppState.READY,
                        activity.kernelForTest().state()
                );
                assertNotNull(activity.shellForTest());
                assertTrue(
                        activity.kernelForTest()
                                .productServices()
                                .resources()
                                .invariantPass()
                );
            });
        }
    }

    @Test
    public void freezeRecoveryRunsOnDevice() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    FreezeEngine freeze = activity.kernelForTest()
                            .productServices()
                            .freeze();
                    if (freeze.state() == FreezeEngine.State.FROZEN) {
                        freeze.thaw();
                    }
                    freeze.freeze();
                    long base = freeze.frozenRevision();
                    assertTrue(base > 0);
                    assertTrue(freeze.hasFrozenBase());

                    Map<String, String> update = new LinkedHashMap<>();
                    update.put(
                            "ui.screen.home.title",
                            "Instrumentation Working"
                    );
                    activity.kernelForTest()
                            .projectManager()
                            .applyResourceTransaction(
                                    update,
                                    Collections.emptySet()
                            );
                    freeze.recover();
                    assertEquals(
                            FreezeEngine.State.FROZEN,
                            freeze.state()
                    );
                    freeze.thaw();
                    assertEquals(
                            FreezeEngine.State.NORMAL,
                            freeze.state()
                    );
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    @Test
    public void editorSoakOneHundredCyclesStaysBounded() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.shellForTest().runSoakForTest(100);
                long drift =
                        activity.shellForTest()
                                .lastSoakPssDriftBytesForTest();
                long startPss =
                        activity.shellForTest()
                                .lastSoakStartPssBytesForTest();
                long endPss =
                        activity.shellForTest()
                                .lastSoakEndPssBytesForTest();
                long peakPss =
                        activity.shellForTest()
                                .lastSoakPeakPssBytesForTest();
                int startThreads =
                        activity.shellForTest()
                                .lastSoakStartThreadsForTest();
                int endThreads =
                        activity.shellForTest()
                                .lastSoakEndThreadsForTest();
                int peakThreads =
                        activity.shellForTest()
                                .lastSoakPeakThreadsForTest();
                long durationMs =
                        activity.shellForTest()
                                .lastSoakDurationMsForTest();
                long maxCycleMs =
                        activity.shellForTest()
                                .lastSoakMaxCycleMsForTest();

                assertTrue("startPss=" + startPss, startPss > 0);
                assertTrue("endPss=" + endPss, endPss > 0);
                assertTrue(
                        "peakPss=" + peakPss,
                        peakPss >= startPss && peakPss >= endPss
                );
                assertTrue(
                        "PSS drift=" + drift,
                        drift <= 96L * 1024L * 1024L
                );
                assertTrue(
                        "thread drift="
                                + startThreads + "->" + endThreads,
                        endThreads <= startThreads + 8
                );
                assertTrue(
                        "peakThreads=" + peakThreads,
                        peakThreads <= startThreads + 16
                );
                assertTrue("durationMs=" + durationMs, durationMs > 0);
                assertTrue(
                        "maxCycleMs=" + maxCycleMs,
                        maxCycleMs < 2500
                );
                assertTrue(
                        activity.kernelForTest()
                                .productServices()
                                .resources()
                                .invariantPass()
                );
            });
        }
    }

    @Test
    public void launcherTbResourceIsBoundAndDecodable() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                int iconRes = activity.getApplicationInfo().icon;
                assertNotEquals(0, iconRes);
                Drawable drawable = activity.getDrawable(iconRes);
                assertNotNull(drawable);
                assertTrue(drawable.getIntrinsicWidth() > 0);
                assertTrue(drawable.getIntrinsicHeight() > 0);
            });
        }
    }

    @Test
    public void externalAssetRendererUsesRealAndroidImageConsumer()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    AppKernel kernel = activity.kernelForTest();
                    Bitmap bitmap = Bitmap.createBitmap(
                            16,
                            16,
                            Bitmap.Config.ARGB_8888
                    );
                    bitmap.eraseColor(0xff00f0b5);
                    ByteArrayOutputStream out =
                            new ByteArrayOutputStream();
                    assertTrue(
                            bitmap.compress(
                                    Bitmap.CompressFormat.PNG,
                                    100,
                                    out
                            )
                    );
                    bitmap.recycle();

                    byte[] bytes = out.toByteArray();
                    String sha = sha256(bytes);
                    String name = "instrumented-image.png";
                    kernel.visibleWorkspaceStore().write(
                            VisibleWorkspaceStore.Area.ASSETS,
                            name,
                            bytes
                    );

                    String id = "asset.external.instrumented";
                    Map<String, String> update = new LinkedHashMap<>();
                    update.put(id + ".storage.area", "Assets");
                    update.put(id + ".storage.name", name);
                    update.put(id + ".sha256", sha);
                    update.put(id + ".kind", "IMAGE");
                    update.put(id + ".mime", "image/png");
                    update.put(id + ".name", "Instrumented Image");
                    kernel.projectManager().applyResourceTransaction(
                            update,
                            Collections.emptySet()
                    );

                    android.view.View rendered =
                            AndroidAssetRenderer.render(
                                    activity,
                                    kernel,
                                    id,
                                    64,
                                    64
                            );
                    assertTrue(rendered instanceof ImageView);
                    assertNotNull(
                            ((ImageView) rendered).getDrawable()
                    );
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    @Test
    public void advancedUiPropertiesMaterializeOnRealAndroidView() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                AppKernel kernel = activity.kernelForTest();
                Map<String, String> update =
                        new LinkedHashMap<>();
                update.put(
                        "ui.object.home.primary.text",
                        "Kirim"
                );
                update.put(
                        "ui.object.home.primary.icon",
                        "★"
                );
                update.put(
                        "ui.object.home.primary.icon.placement",
                        "start"
                );
                update.put(
                        "ui.object.home.primary.color",
                        "#123456"
                );
                update.put(
                        "ui.object.home.primary.border.color",
                        "#00f0b5"
                );
                update.put(
                        "ui.object.home.primary.radius.topleft.dp",
                        "4"
                );
                update.put(
                        "ui.object.home.primary.radius.topright.dp",
                        "10"
                );
                update.put(
                        "ui.object.home.primary.radius.bottomright.dp",
                        "18"
                );
                update.put(
                        "ui.object.home.primary.radius.bottomleft.dp",
                        "24"
                );
                update.put(
                        "ui.object.home.primary.margin.left.dp",
                        "9"
                );
                update.put(
                        "ui.object.home.primary.padding.left.dp",
                        "7"
                );
                update.put(
                        "ui.object.home.primary.padding.right.dp",
                        "11"
                );
                update.put(
                        "ui.object.home.primary.text.weight",
                        "700"
                );
                update.put(
                        "ui.object.home.primary.text.italic",
                        "true"
                );
                update.put(
                        "ui.object.home.primary.text.letterspacing",
                        "0.08"
                );
                update.put(
                        "ui.object.home.primary.text.maxlines",
                        "2"
                );
                update.put(
                        "ui.object.home.primary.rotation",
                        "15"
                );
                update.put(
                        "ui.object.home.primary.scale.x",
                        "1.2"
                );
                update.put(
                        "ui.object.home.primary.scale.y",
                        "1.1"
                );
                update.put(
                        "ui.object.home.primary.accessibility.label",
                        "Tombol Kirim"
                );
                kernel.projectManager().applyResourceTransaction(
                        update,
                        Collections.emptySet()
                );

                UiCanvasView canvas = new UiCanvasView(
                        activity,
                        kernel,
                        null
                );
                TextView button = findTextViewByDescription(
                        canvas,
                        "Tombol Kirim"
                );
                assertNotNull(button);
                assertEquals("★  Kirim", button.getText().toString());
                assertEquals(15f, button.getRotation(), 0.01f);
                assertEquals(1.2f, button.getScaleX(), 0.01f);
                assertEquals(1.1f, button.getScaleY(), 0.01f);
                assertEquals(0.08f, button.getLetterSpacing(), 0.001f);
                assertEquals(2, button.getMaxLines());
                assertTrue(button.getPaddingLeft() > 0);
                assertTrue(button.getPaddingRight() > 0);
                assertNotNull(button.getBackground());
            });
        }
    }

    @Test
    public void memoryPressurePolicyDegradesAndRecoversOnDevice() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ResourceGuard guard = activity.kernelForTest()
                        .productServices()
                        .resources();
                guard.applyPressure(ResourceGuard.Pressure.CRITICAL);
                assertEquals(
                        0.5f,
                        guard.previewQuality(),
                        0.001f
                );
                assertFalse(guard.preloadEnabled());
                guard.applyPressure(ResourceGuard.Pressure.NORMAL);
                assertEquals(
                        1.0f,
                        guard.previewQuality(),
                        0.001f
                );
                assertTrue(guard.preloadEnabled());
            });
        }
    }

    @Test
    public void safeRecoveryUiPersistsAcrossActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.kernelForTest()
                        .safeModeController()
                        .enter();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(
                        activity.kernelForTest()
                                .safeModeController()
                                .isSafeMode()
                );
                assertNull(activity.shellForTest());
                activity.kernelForTest()
                        .safeModeController()
                        .exitIfHealthy();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotNull(activity.shellForTest());
                assertFalse(
                        activity.kernelForTest()
                                .safeModeController()
                                .isSafeMode()
                );
            });
        }
    }

    @Test
    public void patchJournalInterruptedStateRollsBackOnDevice()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    AppKernel kernel = activity.kernelForTest();
                    if (kernel.productServices()
                            .freeze()
                            .state() == FreezeEngine.State.FROZEN) {
                        kernel.productServices().freeze().thaw();
                    }
                    if (kernel.projectManager().hasUnsavedChanges()
                            || kernel.projectManager()
                                .savedRevision() <= 0) {
                        kernel.projectManager().save();
                    }
                    long base =
                            kernel.projectManager().savedRevision();
                    Map<String, String> baseResources =
                            new LinkedHashMap<>(
                                    kernel.projectManager()
                                            .current()
                                            .resources()
                            );
                    PatchPayload payload = new PatchPayload(
                            Collections.singletonMap(
                                    "ui.instrument.patch.crash",
                                    "transient"
                            ),
                            Collections.emptySet()
                    );
                    PatchManifest manifest = new PatchManifest(
                            "patch.instrument." + base,
                            "project.default",
                            base,
                            base + 1,
                            repeat('1'),
                            repeat('2'),
                            repeat('3'),
                            payload.sha256()
                    );
                    kernel.safePatchManager()
                            .journal()
                            .begin(manifest, payload);
                    kernel.safePatchManager()
                            .journal()
                            .phase(
                                    PatchTransactionJournal
                                            .Phase.MUTATING
                            );
                    kernel.projectManager()
                            .applyResourceTransaction(
                                    payload.upserts(),
                                    payload.deletes()
                            );
                    kernel.projectManager().save();

                    File root = new File(
                            activity.getFilesDir(),
                            "projects/project.default"
                    );
                    File assets = new File(
                            activity.getFilesDir(),
                            "library/assets"
                    );
                    AppKernel recovered =
                            AppKernel.createPersistent(
                                    root,
                                    assets
                            );
                    assertTrue(
                            "recovery must publish a new valid revision",
                            recovered.projectManager()
                                    .savedRevision() > base
                    );
                    assertEquals(
                            baseResources,
                            recovered.projectManager()
                                    .current()
                                    .resources()
                    );
                    assertFalse(
                            recovered.projectManager()
                                    .current()
                                    .resources()
                                    .containsKey(
                                            "ui.instrument.patch.crash"
                                    )
                    );
                    assertEquals(
                            PatchTransactionJournal.Phase.IDLE,
                            recovered.safePatchManager()
                                    .journal()
                                    .phase()
                    );
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    @Test
    public void runtimePatchIdentityMatchesInstalledApk()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    InstalledApkIdentity identity =
                            InstalledApkIdentity.read(activity);
                    assertTrue(
                            activity.kernelForTest()
                                    .safePatchManager()
                                    .runtimeApkIdentityBound()
                    );
                    assertEquals(
                            identity.apkSha256(),
                            activity.kernelForTest()
                                    .safePatchManager()
                                    .runtimeParentApkSha256()
                    );
                    assertEquals(
                            BuildConfig.BASELINE_APK_SHA256,
                            activity.kernelForTest()
                                    .safePatchManager()
                                    .runtimeRollbackBaselineApkSha256()
                    );
                    assertEquals(
                            BuildConfig.VERSION_CODE,
                            identity.versionCode()
                    );
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    @Test
    public void managedExternalEditingDoorUsesRealProviderBackedEditor()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    AppKernel local = activity.kernelForTest();
                    Map<String,String> localBefore =
                            new LinkedHashMap<>(
                                    local.projectManager()
                                            .current()
                                            .resources()
                            );

                    ProductCompletionServices.InstalledTargetBridge bridge =
                            local.productServices()
                                    .completion()
                                    .installedTargets;
                    int found = ToolboxAwareTargetDiscovery.discover(
                            activity,
                            bridge
                    );
                    assertTrue("fixture target not found", found >= 1);

                    ProductCompletionServices
                            .InstalledTargetBridge.Target target =
                            bridge.lookup("com.toolbox.fixture");
                    assertNotNull(target);
                    assertTrue(target.hasEditingDoor());
                    assertTrue(target.supportsInternalEditor());
                    assertEquals(
                            "com.toolbox.fixture.toolbox",
                            target.providerAuthority()
                    );
                    assertEquals(
                            "project.fixture",
                            target.projectId()
                    );

                    boolean opened = activity.openManagedTargetEditor(
                            target.packageName(),
                            target.providerAuthority(),
                            target.projectId()
                    );
                    assertTrue(opened);
                    assertTrue(activity.externalTargetActiveForTest());
                    AppKernel external = activity.kernelForTest();
                    assertEquals(
                            "project.fixture",
                            external.projectManager()
                                    .current()
                                    .projectId()
                    );
                    assertEquals(
                            "Fixture Original",
                            external.projectManager()
                                    .current()
                                    .resources()
                                    .get("ui.screen.home.title")
                    );

                    Map<String,String> edit = new LinkedHashMap<>();
                    edit.put(
                            "ui.screen.home.title",
                            "Fixture Edited by ToolBox"
                    );
                    external.projectManager()
                            .applyResourceTransaction(
                                    edit,
                                    Collections.emptySet()
                            );
                    external.projectManager().save();

                    ManagedAppProjectStore fresh =
                            new ManagedAppProjectStore(
                                    activity.getContentResolver(),
                                    activity.getPackageManager(),
                                    target.packageName(),
                                    target.providerAuthority(),
                                    target.projectId()
                            );
                    assertEquals(
                            "Fixture Edited by ToolBox",
                            fresh.load("project.fixture")
                                    .state()
                                    .resources()
                                    .get("ui.screen.home.title")
                    );

                    assertTrue(activity.returnToToolBoxProject());
                    assertFalse(activity.externalTargetActiveForTest());
                    assertEquals(
                            localBefore,
                            activity.kernelForTest()
                                    .projectManager()
                                    .current()
                                    .resources()
                    );
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    private static TextView findTextViewByDescription(
            View view,
            String description
    ) {
        if (view instanceof TextView
                && description.contentEquals(
                        view.getContentDescription()
                )) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextViewByDescription(
                        group.getChildAt(i),
                        description
                );
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte value : digest) {
            out.append(String.format(
                    Locale.ROOT,
                    "%02x",
                    value
            ));
        }
        return out.toString();
    }

    private static String repeat(char value) {
        char[] out = new char[64];
        Arrays.fill(out, value);
        return new String(out);
    }

}
