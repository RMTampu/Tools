package io.toolbox.stageahosttest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;
import io.toolbox.stagea.android.AndroidAtomicStateStore;
import io.toolbox.stagea.android.AndroidPermissionStateProvider;
import io.toolbox.stagea.android.AndroidRecoveryStateStore;
import io.toolbox.stagea.android.AndroidResourcePolicyProvider;
import io.toolbox.stagea.android.AndroidSafeUi;
import io.toolbox.stagea.android.AndroidStageAHost;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

public final class HostTestActivity extends Activity {
    private static final String TAG="ToolBoxStageAHost";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode=getIntent().getStringExtra("mode");
        try {
            if ("write".equals(mode)) writePhase();
            else if ("read".equals(mode)) readPhase();
            else if ("corrupt".equals(mode)) corruptPhase();
            else if ("ui".equals(mode)) uiPhase();
            else if ("referenceDummy".equals(mode)) dummyPhase(new ReferenceDummyReceiver(),"STAGE_A_REFERENCE_DUMMY_PASS");
            else if ("adversarialDummy".equals(mode)) dummyPhase(new AdversarialConformantDummyReceiver(),"STAGE_A_ADVERSARIAL_DUMMY_PASS");
            else throw new IllegalArgumentException("unknown mode");
        } catch(Throwable failure) {
            Log.e(TAG,"STAGE_A_HOST_TEST_FAIL mode="+mode,failure);
            throw new RuntimeException(failure);
        }
    }
    private void writePhase() {
        AndroidAtomicStateStore store=new AndroidAtomicStateStore(this);
        store.clear();
        store.put("kernel.state","STOPPED");
        new AndroidRecoveryStateStore(store).save(SafetyContracts.RecoveryState.SAFE_MODE);
        Log.i(TAG,"STAGE_A_HOST_WRITE_PASS");
    }
    private void readPhase() {
        AndroidAtomicStateStore store=new AndroidAtomicStateStore(this);
        store.clear();
        store.put("kernel.state","STOPPED");
        new AndroidRecoveryStateStore(store).save(SafetyContracts.RecoveryState.SAFE_MODE);
        AndroidStageAHost host=AndroidStageAHost.createStageA(this);
        check(host.bootstrap()==SafetyContracts.RecoveryState.SAFE_MODE);
        check(host.safeUiModel().visible() && host.safeUiModel().restricted());
        ProductRegistry registry=host.productRegistry();
        check(registry!=null && registry==host.productRegistry());
        Contracts.PermissionRequirement permission=new Contracts.PermissionRequirement(
                "permission.vibrate", Contracts.PermissionKind.INSTALL_TIME,
                "android.permission.VIBRATE","permission.vibrate.reason","permission.denied","permission.unsupported");
        Contracts.ToolContract tool=new Contracts.ToolContract(
                "tool.test","1.0.0","1.0.0",Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),
                Collections.emptyList(),Collections.emptyList(),Collections.singletonList("permission.vibrate"),"entry.test");
        host.publish(new Contracts.ToolBundle(tool,Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),
                Collections.emptyList(),Collections.singletonList(permission)));
        check(host.registrySnapshot().permissions().containsKey("permission.vibrate"));
        check(registry.snapshot().totalEntries()==host.registrySnapshot().totalEntries());
        AndroidPermissionStateProvider permissions=new AndroidPermissionStateProvider(this,registry);
        check(permissions.permissionState("permission.vibrate")==StageAContracts.Availability.AVAILABLE);
        check(permissions.permissionState("permission.missing")==StageAContracts.Availability.UNAVAILABLE);
        AndroidResourcePolicyProvider resources=AndroidResourcePolicyProvider.stageAIdle(this);
        SafetyContracts.ResourceBudget budget=resources.budgetFor("tool.test");
        SafetyContracts.ResourceSample sample=resources.sampleFor("tool.test");
        check(budget.memoryUnits()==10000 && sample.workUnits()==0 && sample.concurrentOperations()==0);
        Log.i(TAG,"STAGE_A_HOST_SHARED_REGISTRY_PASS");
        Log.i(TAG,"STAGE_A_HOST_READ_PASS");
    }
    private void corruptPhase() throws Exception {
        AndroidAtomicStateStore store=new AndroidAtomicStateStore(this);
        store.clear();
        store.put("kernel.state","STOPPED");
        File file=new File(getFilesDir(),AndroidAtomicStateStore.DEFAULT_RELATIVE_PATH);
        try(FileOutputStream out=new FileOutputStream(file,false)){ out.write(new byte[]{1,2,3,4,5}); out.getFD().sync(); }
        boolean failed=false; try { store.get("kernel.state"); } catch(RuntimeException expected){ failed=true; }
        check(failed);
        Log.i(TAG,"STAGE_A_HOST_CORRUPTION_REJECT_PASS");
    }
    private void uiPhase() {
        AndroidAtomicStateStore store=new AndroidAtomicStateStore(this);
        store.clear();
        new AndroidRecoveryStateStore(store).save(SafetyContracts.RecoveryState.SAFE_MODE);
        AndroidStageAHost host=AndroidStageAHost.createStageA(this);
        check(host.bootstrap()==SafetyContracts.RecoveryState.SAFE_MODE);
        StageAContracts.SafeUiModel model=host.safeUiModel();
        check(model.visible() && model.restricted());
        AndroidSafeUi.Actions actions=host.safeUiActions();
        check(actions.verifyIntegrity().contains("health="));
        check(actions.retryBootstrap().contains("SAFE_MODE"));
        check(actions.enterReadOnly().contains("Mode aman"));
        check(actions.exportSanitizedDiagnostics().contains("count="));
        check(!actions.canRestoreKnownGood());
        check(actions.restoreKnownGood().contains("belum memiliki authority"));
        check(actions.canQuarantine());
        View view=AndroidSafeUi.render(this,model,"Pemeriksaan keselamatan diperlukan.",actions);
        check(view!=null);
        setContentView(view);
        check(actions.quarantine().contains("QUARANTINED"));
        check(host.safeUiModel().recoveryState()==SafetyContracts.RecoveryState.QUARANTINED);
        check(!actions.canQuarantine());
        Log.i(TAG,"STAGE_A_HOST_SAFE_UI_ACTIONS_PASS");
        Log.i(TAG,"STAGE_A_HOST_SAFE_UI_PASS");
    }
    private void dummyPhase(DummyReceiver receiver,String marker) {
        receiver.verify(this);
        check(receiver.receiverId()!=null && !receiver.receiverId().isEmpty());
        Log.i(TAG,marker+" receiver="+receiver.receiverId());
    }
    private static void check(boolean v){ if(!v) throw new AssertionError(); }
}
