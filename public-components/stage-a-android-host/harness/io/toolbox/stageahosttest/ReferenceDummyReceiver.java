package io.toolbox.stageahosttest;

import android.app.Activity;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;
import io.toolbox.stagea.android.AndroidAtomicStateStore;
import io.toolbox.stagea.android.AndroidStageAHost;

final class ReferenceDummyReceiver implements DummyReceiver {
    @Override public String receiverId() { return "reference"; }

    @Override public void verify(Activity activity) {
        AndroidAtomicStateStore clean = new AndroidAtomicStateStore(activity);
        clean.clear();

        AndroidStageAHost host = AndroidStageAHost.createStageA(activity);
        require(host.bootstrap() == SafetyContracts.RecoveryState.NORMAL);
        StageAContracts.SafeUiModel ui = host.safeUiModel();
        require(!ui.visible() && !ui.restricted());
        require(host.registrySnapshot() != null);
        require(host.durableStateStore() != null);
        require(host.diagnostics() != null);
        require(host.health() != null);
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError("reference dummy contract mismatch");
    }
}
