package com.toolbox.tools.integration;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public final class ExportSyncTest {
    @Test
    public void exportIsDeterministicChecksummedAndReadOnly() {
        ExternalIntegrationManager manager =
                new ExternalIntegrationManager();
        ExternalSnapshot snapshot =
                manager.demoSnapshot(1, "cursor.export.1");
        NormalizationResult normalized =
                manager.importSnapshot(snapshot);

        int localBefore = manager.sync().local().size();
        ExportPackage first = manager.export(normalized.records());
        ExportPackage second = manager.export(normalized.records());

        assertEquals(first.payload(), second.payload());
        assertEquals(first.sha256(), second.sha256());
        assertTrue(first.sha256().matches("[0-9a-f]{64}"));
        assertTrue(first.payload().startsWith("TBX_EXTERNAL_V1"));
        assertEquals(localBefore, manager.sync().local().size());
    }

    @Test
    public void syncIsIdempotentAndConflictIsExplicit() {
        ExternalIntegrationManager manager =
                new ExternalIntegrationManager();
        ExternalSnapshot first =
                manager.demoSnapshot(1, "cursor.sync.1");
        SyncPlan initial = manager.planSync(first);

        assertEquals(SyncStatus.CLEAN, initial.status());
        manager.applySync(initial);
        assertEquals("cursor.sync.1", manager.sync().cursor());

        SyncPlan same = manager.planSync(first);
        assertEquals(SyncStatus.NO_CHANGE, same.status());
        int historyBefore = manager.sync().historySize();
        manager.applySync(same);
        assertEquals(historyBefore, manager.sync().historySize());

        manager.sync().markLocalDirty();
        ExternalSnapshot remoteChanged =
                manager.demoSnapshot(2, "cursor.sync.2");
        SyncPlan conflict = manager.planSync(remoteChanged);

        assertEquals(SyncStatus.CONFLICT, conflict.status());
        assertThrows(
                IllegalStateException.class,
                () -> manager.applySync(conflict)
        );
        assertEquals("cursor.sync.1", manager.sync().cursor());
    }

    @Test
    public void syncHistoryIsBounded() {
        ExternalIntegrationManager manager =
                new ExternalIntegrationManager();

        for (int i = 0; i < SyncEngine.MAX_HISTORY + 10; i++) {
            ExternalSnapshot snapshot = manager.demoSnapshot(
                    i + 1,
                    "cursor." + (i + 1)
            );
            SyncPlan plan = manager.planSync(snapshot);
            assertEquals(SyncStatus.CLEAN, plan.status());
            manager.applySync(plan);
        }

        assertEquals(
                SyncEngine.MAX_HISTORY,
                manager.sync().historySize()
        );
    }

    @Test
    public void adapterCapabilitiesAndIndonesianPresentationAreExplicit() {
        ExternalIntegrationManager manager =
                new ExternalIntegrationManager();

        assertEquals(
                "Sumber Demo",
                manager.adapter().labelIndonesia()
        );
        assertTrue(manager.adapter()
                .supports(ExternalCapability.IMPORT));
        assertTrue(manager.adapter()
                .supports(ExternalCapability.EXPORT));
        assertTrue(manager.adapter()
                .supports(ExternalCapability.SYNC));
    }
}
