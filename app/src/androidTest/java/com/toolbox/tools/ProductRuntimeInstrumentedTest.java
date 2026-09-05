package com.toolbox.tools;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.toolbox.tools.core.AppState;
import com.toolbox.tools.product.FreezeEngine;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.LinkedHashMap;
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
                assertTrue(
                        "PSS drift=" + drift,
                        drift <= 96L * 1024L * 1024L
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
}
