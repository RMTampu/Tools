package io.toolbox.stageahosttest;

import android.app.Activity;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;
import io.toolbox.stagea.android.AndroidAtomicStateStore;
import io.toolbox.stagea.android.AndroidRecoveryStateStore;
import io.toolbox.stagea.android.AndroidResourcePolicyProvider;
import io.toolbox.stagea.android.AndroidStageAHost;

final class AdversarialConformantDummyReceiver implements DummyReceiver {
    @Override public String receiverId() { return "adversarial-conformant"; }

    @Override public void verify(Activity activity) {
        AndroidAtomicStateStore seed = new AndroidAtomicStateStore(activity);
        seed.clear();
        new AndroidRecoveryStateStore(seed).save(SafetyContracts.RecoveryState.SAFE_MODE);

        AndroidResourcePolicyProvider constrained = new AndroidResourcePolicyProvider(
                activity,
                new AndroidResourcePolicyProvider.WorkloadProbe() {
                    @Override public int measuredWorkCapacity() { return 1; }
                    @Override public int measuredConcurrentCapacity() { return 1; }
                    @Override public int currentWorkUnits() { return 0; }
                    @Override public int currentConcurrentOperations() { return 0; }
                });

        AndroidStageAHost host = AndroidStageAHost.create(
                activity,
                capabilityId -> StageAContracts.Availability.UNAVAILABLE,
                constrained);
        require(host.bootstrap() == SafetyContracts.RecoveryState.SAFE_MODE);
        StageAContracts.SafeUiModel ui = host.safeUiModel();
        require(ui.visible() && ui.restricted());
        require(host.registrySnapshot() != null);
        require(host.durableStateStore() != null);
        require(host.diagnostics() != null);
        require(host.health() != null);
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError("adversarial dummy contract mismatch");
    }
}
