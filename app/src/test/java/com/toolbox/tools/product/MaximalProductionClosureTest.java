package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.FileRuntimeStateStore;
import com.toolbox.tools.core.FileProjectStore;
import com.toolbox.tools.core.FileVisibleWorkspaceStore;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.RuntimeStateStore;
import com.toolbox.tools.core.VisibleWorkspaceStore;
import com.toolbox.tools.delivery.PatchManifest;
import com.toolbox.tools.delivery.PatchPayload;
import com.toolbox.tools.delivery.PatchTransactionJournal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public final class MaximalProductionClosureTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void runtimeStateSurvivesFreshStoreInstance() throws Exception {
        File file = new File(temp.newFolder("state"), "runtime.properties");
        RuntimeStateStore first = new FileRuntimeStateStore(file);
        first.put("safe.mode", "true");
        first.put("freeze.state", "FROZEN");

        RuntimeStateStore second = new FileRuntimeStateStore(file);
        assertEquals("true", second.get("safe.mode"));
        assertEquals("FROZEN", second.get("freeze.state"));
        second.remove("safe.mode");

        RuntimeStateStore third = new FileRuntimeStateStore(file);
        assertNull(third.get("safe.mode"));
        assertEquals("FROZEN", third.get("freeze.state"));
    }

    @Test
    public void safeModeAndFreezeSurviveKernelRecreation() throws Exception {
        File projectRoot = temp.newFolder("persistent-project");
        File assets = temp.newFolder("persistent-assets");

        AppKernel first = AppKernel.createPersistent(projectRoot, assets);
        if (first.projectManager().savedRevision() <= 0) {
            first.projectManager().save();
        }
        first.safeModeController().enter();
        first.productServices().freeze().freeze();
        long frozen = first.productServices().freeze().frozenRevision();
        assertTrue(frozen > 0);

        AppKernel second = AppKernel.createPersistent(projectRoot, assets);
        assertTrue(second.safeModeController().isSafeMode());
        assertEquals(
                FreezeEngine.State.FROZEN,
                second.productServices().freeze().state()
        );
        assertEquals(
                frozen,
                second.productServices().freeze().frozenRevision()
        );
    }

    @Test
    public void interruptedFreezeJournalRecoversFrozenBase() throws Exception {
        File projectRoot = temp.newFolder("freeze-interrupt");
        File assets = temp.newFolder("freeze-interrupt-assets");

        AppKernel first = AppKernel.createPersistent(projectRoot, assets);
        first.productServices().freeze().freeze();
        long base = first.productServices().freeze().frozenRevision();

        first.runtimeStateStore().put("freeze.state", "COMMITTING");
        first.runtimeStateStore().put(
                "freeze.journal.operation",
                "COMMIT"
        );
        first.runtimeStateStore().put(
                "freeze.journal.phase",
                "SAVE_OVERLAY"
        );

        AppKernel recovered = AppKernel.createPersistent(
                projectRoot,
                assets
        );
        assertEquals(
                FreezeEngine.State.FROZEN,
                recovered.productServices().freeze().state()
        );
        assertEquals(
                base,
                recovered.productServices().freeze().frozenRevision()
        );
        assertFalse(
                recovered.recoveryManager().isRecoveryRequired()
        );
    }

    @Test
    public void interruptedPatchJournalRollsBackOnBootstrap() throws Exception {
        File projectRoot = temp.newFolder("patch-interrupt");
        File assets = temp.newFolder("patch-interrupt-assets");

        AppKernel first = AppKernel.createPersistent(projectRoot, assets);
        if (first.projectManager().savedRevision() <= 0) {
            first.projectManager().save();
        }
        long base = first.projectManager().savedRevision();

        Map<String, String> upserts = new LinkedHashMap<>();
        upserts.put("ui.test.patch.interrupted", "working");
        PatchPayload payload = new PatchPayload(
                upserts,
                Collections.emptySet()
        );
        PatchManifest manifest = legacyManifest(payload, base);
        first.safePatchManager().journal().begin(manifest, payload);
        first.safePatchManager().journal().phase(
                PatchTransactionJournal.Phase.MUTATING
        );

        first.projectManager().applyResourceTransaction(
                upserts,
                Collections.emptySet()
        );
        first.projectManager().save();
        assertTrue(
                first.projectManager().current().resources()
                        .containsKey("ui.test.patch.interrupted")
        );

        AppKernel recovered = AppKernel.createPersistent(
                projectRoot,
                assets
        );
        // Recovery dipublikasikan sebagai revision valid baru agar history
        // tetap append-only; semantic state harus identik dengan baseline.
        assertTrue(
                recovered.projectManager().savedRevision() > base
        );
        ProjectState restoredBase =
                recovered.projectManager().previewRecovery(base);
        assertEquals(
                restoredBase.resources(),
                recovered.projectManager().current().resources()
        );
        assertFalse(
                recovered.projectManager().current().resources()
                        .containsKey("ui.test.patch.interrupted")
        );
        assertEquals(
                PatchTransactionJournal.Phase.IDLE,
                recovered.safePatchManager().journal().phase()
        );
        assertFalse(
                recovered.recoveryManager().isRecoveryRequired()
        );
    }

    @Test
    public void visibleWorkspaceStreamsAndPersistsEveryRequiredArea()
            throws Exception {
        File root = temp.newFolder("visible");
        FileVisibleWorkspaceStore store =
                new FileVisibleWorkspaceStore(root);
        store.ensureLayout();

        for (VisibleWorkspaceStore.Area area
                : VisibleWorkspaceStore.Area.values()) {
            byte[] value = ("content-" + area.name())
                    .getBytes(StandardCharsets.UTF_8);
            VisibleWorkspaceStore.WriteResult result =
                    store.writeStream(
                            area,
                            "proof.bin",
                            new ByteArrayInputStream(value),
                            1024
                    );
            assertEquals(value.length, result.bytesWritten());
            assertTrue(result.sha256().matches("[0-9a-f]{64}"));
            assertTrue(store.exists(area, "proof.bin"));
            assertArrayEquals(value, store.read(area, "proof.bin"));
            assertEquals(
                    Collections.singletonList("proof.bin"),
                    store.list(area)
            );
        }

        FileVisibleWorkspaceStore reopened =
                new FileVisibleWorkspaceStore(root);
        for (VisibleWorkspaceStore.Area area
                : VisibleWorkspaceStore.Area.values()) {
            assertTrue(reopened.exists(area, "proof.bin"));
        }
    }

    @Test
    public void backupIsPhysicalAndCanBeDiscoveredAfterManagerRecreation()
            throws Exception {
        File projectRoot = temp.newFolder("backup-project");
        File assets = temp.newFolder("backup-assets");
        File visibleRoot = temp.newFolder("backup-visible");

        AppKernel kernel = AppKernel.createPersistent(
                projectRoot,
                assets
        );
        FileVisibleWorkspaceStore visible =
                new FileVisibleWorkspaceStore(visibleRoot);
        BackupManager first = new BackupManager(
                kernel.projectManager(),
                visible
        );
        BackupManager.BackupRecord record = first.createVerified();

        assertTrue(
                visible.exists(
                        VisibleWorkspaceStore.Area.BACKUPS,
                        record.fileName()
                )
        );

        BackupManager second = new BackupManager(
                kernel.projectManager(),
                new FileVisibleWorkspaceStore(visibleRoot)
        );
        assertFalse(second.records().isEmpty());
        assertEquals(
                record.revision(),
                second.records().get(0).revision()
        );
    }

    @Test
    public void realRecoverySnapshotsLiveInVisibleSnapshotsArea()
            throws Exception {
        File storeRoot = temp.newFolder("snapshot-project-store");
        File privateRoot = temp.newFolder("snapshot-private");
        File assets = temp.newFolder("snapshot-assets");
        File visibleRoot = temp.newFolder("snapshot-visible");
        FileVisibleWorkspaceStore visible =
                new FileVisibleWorkspaceStore(visibleRoot);

        AppKernel kernel = AppKernel.createPersistent(
                new FileProjectStore(storeRoot),
                "project.default",
                privateRoot,
                assets,
                visible
        );
        if (kernel.projectManager().savedRevision() <= 0) {
            kernel.projectManager().save();
        }
        kernel.projectManager().captureFinalRecoverySnapshot();

        assertTrue(
                visible.exists(
                        VisibleWorkspaceStore.Area.SNAPSHOTS,
                        "last-valid-recovery.tbx"
                )
        );
        assertTrue(
                visible.exists(
                        VisibleWorkspaceStore.Area.SNAPSHOTS,
                        "final-recovery.tbx"
                )
        );
        assertFalse(
                new File(privateRoot, "recovery").exists()
        );
    }

    @Test
    public void patchManifestV2ClosesCompatibilityCapabilitiesAndFiles() {
        PatchPayload payload = new PatchPayload(
                Collections.singletonMap(
                        "ui.patch.v2",
                        "ok"
                ),
                Collections.emptySet()
        );
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("payload", payload.sha256());

        Set<String> requiredCapabilities =
                new LinkedHashSet<>(
                        Arrays.asList("ui", "asset")
                );
        PatchManifest manifest = new PatchManifest(
                "patch.v2.test",
                "project.default",
                1,
                2,
                sha('a'),
                sha('b'),
                sha('c'),
                payload.sha256(),
                "DECLARATIVE_PATCH",
                "com.toolbox.tools",
                "13.0-test",
                13,
                13,
                Collections.emptySet(),
                requiredCapabilities,
                hashes,
                "EVOLUTION"
        );

        assertEquals(2, manifest.schemaVersion());
        assertTrue(
                manifest.supportsHost(
                        "com.toolbox.tools",
                        13,
                        requiredCapabilities
                )
        );
        assertFalse(
                manifest.supportsHost(
                        "com.toolbox.tools",
                        12,
                        requiredCapabilities
                )
        );
        assertFalse(
                manifest.supportsHost(
                        "com.toolbox.tools",
                        13,
                        Collections.singleton("ui")
                )
        );
    }

    @Test
    public void memoryPressureActuallyReducesWorkingSetPolicy() {
        ResourceGuard guard = new ResourceGuard();
        guard.applyPressure(ResourceGuard.Pressure.NORMAL);
        assertEquals(1.0f, guard.previewQuality(), 0.001f);
        assertTrue(guard.preloadEnabled());

        guard.applyPressure(ResourceGuard.Pressure.REDUCED);
        assertEquals(0.75f, guard.previewQuality(), 0.001f);
        assertFalse(guard.preloadEnabled());

        int before = guard.releaseGeneration();
        guard.applyPressure(ResourceGuard.Pressure.CRITICAL);
        assertEquals(0.5f, guard.previewQuality(), 0.001f);
        assertFalse(guard.preloadEnabled());
        assertTrue(guard.releaseGeneration() > before);
        assertTrue(guard.invariantPass());
    }

    @Test
    public void scaleClassesMaterializeRealProjectGraphs() {
        ScaleBenchmarkHarness harness =
                new ScaleBenchmarkHarness();
        int previousResources = 0;
        int previousReferences = 0;
        for (ScaleBenchmarkHarness.ScaleClass scale
                : ScaleBenchmarkHarness.ScaleClass.values()) {
            ScaleBenchmarkHarness.Result result =
                    harness.runActual(
                            scale,
                            96L * 1024L * 1024L
                    );
            assertTrue(result.withinBudget());
            assertTrue(result.roundTripEqual());
            assertTrue(result.encodedProjectBytes() > 0);
            assertTrue(
                    result.resourceCount()
                            > previousResources
            );
            assertTrue(
                    result.referenceCount()
                            >= previousReferences
            );
            assertTrue(
                    result.resourceCount()
                            <= ProjectState.MAX_RESOURCES
            );
            assertTrue(
                    result.referenceCount()
                            <= ProjectState.MAX_REFERENCES
            );
            previousResources = result.resourceCount();
            previousReferences = result.referenceCount();
        }
    }

    private static PatchManifest legacyManifest(
            PatchPayload payload,
            long base
    ) {
        return new PatchManifest(
                "patch.interrupted." + base,
                "project.default",
                base,
                base + 1,
                sha('1'),
                sha('2'),
                sha('3'),
                payload.sha256()
        );
    }

    private static String sha(char value) {
        char[] out = new char[64];
        Arrays.fill(out, value);
        return new String(out);
    }
}
