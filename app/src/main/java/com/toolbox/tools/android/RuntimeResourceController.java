package com.toolbox.tools.android;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Debug;
import android.view.View;
import android.view.ViewGroup;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.product.ResourceGuard;

import java.util.Objects;

public final class RuntimeResourceController {
    private final Context context;
    private final AppKernel kernel;

    public RuntimeResourceController(
            Context context,
            AppKernel kernel
    ) {
        this.context = Objects.requireNonNull(
                context,
                "context"
        ).getApplicationContext();
        this.kernel = Objects.requireNonNull(
                kernel,
                "kernel"
        );
        configureBudgetFromDevice();
    }

    public synchronized ResourceGuard.Pressure sample(
            View root
    ) {
        long pssBytes = Debug.getPss() * 1024L;
        int views = countViews(root);
        int heavyAssets = activeHeavyAssets();
        String screenId = kernel.productServices()
                .resources()
                .activeScreenId();
        if (screenId == null) {
            screenId = "screen.home";
            kernel.productServices()
                    .resources()
                    .enterScreen(screenId);
        }
        ResourceGuard.Pressure pressure =
                kernel.productServices()
                        .resources()
                        .sample(
                                screenId,
                                pssBytes,
                                views,
                                heavyAssets
                        );
        apply(pressure);
        return pressure;
    }

    public synchronized void onTrimMemory(
            int level,
            View root
    ) {
        ResourceGuard.Pressure pressure;
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            pressure = ResourceGuard.Pressure.CRITICAL;
        } else if (level
                >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            pressure = ResourceGuard.Pressure.REDUCED;
        } else {
            pressure = sample(root);
        }
        apply(pressure);
    }

    public synchronized void onLowMemory() {
        apply(ResourceGuard.Pressure.CRITICAL);
    }

    public synchronized void onUiHidden() {
        kernel.productServices()
                .cache()
                .clearDisposable();
        String active = kernel.productServices()
                .resources()
                .activeScreenId();
        if (active != null) {
            kernel.productServices()
                    .resources()
                    .releaseScreen(active);
        }
    }

    private void apply(ResourceGuard.Pressure pressure) {
        kernel.productServices()
                .resources()
                .applyPressure(pressure);
        switch (pressure) {
            case CRITICAL:
                kernel.productServices()
                        .cache()
                        .setBudgetBytes(8L * 1024L * 1024L);
                kernel.productServices()
                        .cache()
                        .clearDisposable();
                break;
            case REDUCED:
                kernel.productServices()
                        .cache()
                        .setBudgetBytes(24L * 1024L * 1024L);
                kernel.productServices()
                        .cache()
                        .clearDisposable();
                break;
            case NORMAL:
            default:
                kernel.productServices()
                        .cache()
                        .setBudgetBytes(64L * 1024L * 1024L);
                break;
        }
    }

    private void configureBudgetFromDevice() {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(
                        Context.ACTIVITY_SERVICE
                );
        if (manager == null) return;

        long memoryClassBytes =
                manager.getMemoryClass()
                        * 1024L * 1024L;
        long bounded = Math.max(
                96L * 1024L * 1024L,
                Math.min(
                        256L * 1024L * 1024L,
                        memoryClassBytes / 2
                )
        );
        kernel.productServices()
                .resources()
                .setMemoryBudgetBytes(bounded);
    }

    private int activeHeavyAssets() {
        String id = kernel.projectManager()
                .current()
                .resources()
                .get("asset.editor.active");
        if (id == null) return 0;
        String kind = kernel.projectManager()
                .current()
                .resources()
                .get(id + ".kind");
        if ("VIDEO".equals(kind)
                || "AUDIO".equals(kind)
                || "IMAGE".equals(kind)) {
            return 1;
        }
        return 0;
    }

    private static int countViews(View root) {
        if (root == null) return 0;
        int count = 1;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countViews(group.getChildAt(i));
            }
        }
        return count;
    }
}
