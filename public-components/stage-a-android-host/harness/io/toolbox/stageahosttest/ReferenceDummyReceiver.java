package io.toolbox.stageahosttest;

import android.app.Activity;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.android.AndroidAtomicStateStore;
import io.toolbox.stagea.android.AndroidStageAHost;

final class ReferenceDummyReceiver implements DummyReceiver {
    @Override public String receiverId() { return "reference"; }

    @Override public void verify(Activity activity) {
        AndroidAtomicStateStore clean = new AndroidAtomicStateStore(activity);
        clean.clear();

        ContractDrivenDummyIntegrationPlane plane = new ContractDrivenDummyIntegrationPlane();
        AndroidStageAHost host = AndroidStageAHost.createStageA(activity);
        plane.bindProvider(host);
        require(plane.registry() == host.productRegistry());
        require(plane.bootstrap() == SafetyContracts.RecoveryState.NORMAL);
        plane.markKernelReady();
        require(!plane.routeRestrictedBeforeNormal());
        plane.verifyClosed();
        require(plane.registry() == host.productRegistry());
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError("reference dummy contract mismatch");
    }
}
