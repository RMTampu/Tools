package com.toolbox.tools.debug;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.delivery.PatchManifest;
import com.toolbox.tools.delivery.PatchPayload;
import com.toolbox.tools.delivery.PatchTransactionJournal;
import com.toolbox.tools.product.FreezeEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug-only destructive process-death harness.
 * It is never merged into release because it lives in src/debug.
 */
public final class CrashHarnessActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PHASE = "phase";
    public static final String MARKER_KEY =
            "ui.crash.transaction.marker";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        String phase = getIntent().getStringExtra(EXTRA_PHASE);
        if (mode == null) mode = "patch";
        if (phase == null) phase = "MUTATING";

        try {
            AppKernel kernel = AppKernel.createPersistent(
                    new File(
                            getFilesDir(),
                            "projects/project.default"
                    ),
                    new File(
                            getFilesDir(),
                            "library/assets"
                    )
            );
            if ("freeze".equals(mode)) {
                prepareFreezeCrash(kernel, phase);
            } else {
                preparePatchCrash(kernel, phase);
            }
            writeSuccessMarker(mode, phase);
        } catch (Exception error) {
            getSharedPreferences(
                    "toolbox.crash.harness",
                    MODE_PRIVATE
            ).edit()
                    .putString(
                            "prepare.error",
                            error.getClass().getName()
                                    + ":"
                                    + error.getMessage()
                    )
                    .commit();
        } finally {
            Process.killProcess(Process.myPid());
            System.exit(23);
        }
    }

    private static void preparePatchCrash(
            AppKernel kernel,
            String phaseName
    ) throws Exception {
        if (kernel.projectManager().hasUnsavedChanges()
                || kernel.projectManager().savedRevision() <= 0) {
            kernel.projectManager().save();
        }
        long base = kernel.projectManager().savedRevision();

        Map<String, String> upserts = new LinkedHashMap<>();
        upserts.put(
                MARKER_KEY,
                "interrupted." + phaseName
        );
        PatchPayload payload = new PatchPayload(
                upserts,
                Collections.emptySet()
        );
        PatchManifest manifest = new PatchManifest(
                "patch.crash."
                        + phaseName.toLowerCase(
                            java.util.Locale.ROOT
                        )
                        + "."
                        + base,
                "project.default",
                base,
                base + 1,
                hex('1'),
                hex('2'),
                hex('3'),
                payload.sha256()
        );

        PatchTransactionJournal journal =
                kernel.safePatchManager().journal();
        journal.begin(manifest, payload);

        PatchTransactionJournal.Phase phase =
                PatchTransactionJournal.Phase.valueOf(phaseName);
        if (phase.ordinal()
                >= PatchTransactionJournal.Phase.SNAPSHOT_READY.ordinal()) {
            kernel.projectManager().captureFinalRecoverySnapshot();
            journal.phase(
                    PatchTransactionJournal.Phase.SNAPSHOT_READY
            );
        }
        if (phase.ordinal()
                >= PatchTransactionJournal.Phase.MUTATING.ordinal()) {
            journal.phase(
                    PatchTransactionJournal.Phase.MUTATING
            );
            kernel.projectManager().applyResourceTransaction(
                    upserts,
                    Collections.emptySet()
            );
            kernel.projectManager().save();
        }
        if (phase.ordinal()
                >= PatchTransactionJournal.Phase.VERIFYING.ordinal()) {
            journal.phase(
                    PatchTransactionJournal.Phase.VERIFYING
            );
        }
        if (phase.ordinal()
                >= PatchTransactionJournal.Phase.HEALTH_CHECK.ordinal()) {
            journal.phase(
                    PatchTransactionJournal.Phase.HEALTH_CHECK
            );
        }
        if (phase.ordinal()
                >= PatchTransactionJournal.Phase.COMMITTING.ordinal()) {
            journal.phase(
                    PatchTransactionJournal.Phase.COMMITTING
            );
        }
    }

    private static void prepareFreezeCrash(
            AppKernel kernel,
            String phase
    ) throws Exception {
        FreezeEngine freeze = kernel.productServices().freeze();
        if (freeze.state() == FreezeEngine.State.FROZEN) {
            freeze.thaw();
        }
        freeze.freeze();

        kernel.runtimeStateStore().put(
                "freeze.state",
                phase
        );
        kernel.runtimeStateStore().put(
                "freeze.journal.operation",
                "CRASH_HARNESS"
        );
        kernel.runtimeStateStore().put(
                "freeze.journal.phase",
                phase
        );
    }

    private void writeSuccessMarker(
            String mode,
            String phase
    ) throws Exception {
        File target = new File(
                getFilesDir(),
                "crash-harness-last.txt"
        );
        File pending = new File(
                getFilesDir(),
                "crash-harness-last.txt.pending"
        );
        byte[] value = (
                mode + ":" + phase + "\n"
        ).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output =
                     new FileOutputStream(pending)) {
            output.write(value);
            output.flush();
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException(
                    "crash harness marker replace failed"
            );
        }
        if (!pending.renameTo(target)) {
            throw new IllegalStateException(
                    "crash harness marker publish failed"
            );
        }
    }

    private static String hex(char value) {
        char[] out = new char[64];
        Arrays.fill(out, value);
        return new String(out);
    }
}
