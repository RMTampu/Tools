package io.toolbox.stageahosttest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.SafeUiPolicy;
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
        check("STOPPED".equals(store.get("kernel.state")));
        check(new AndroidRecoveryStateStore(store).load()==SafetyContracts.RecoveryState.SAFE_MODE);
        AndroidStageAHost host=AndroidStageAHost.createStageA(this);
        check(host.bootstrap()==SafetyContracts.RecoveryState.SAFE_MODE);
        check(host.safeUiModel().visible() && host.safeUiModel().restricted());
        ProductRegistry registry=new ProductRegistry();
        Contracts.PermissionRequirement permission=new Contracts.PermissionRequirement(
                "permission.vibrate", Contracts.PermissionKind.INSTALL_TIME,
                "android.permission.VIBRATE","permission.vibrate.reason","permission.denied","permission.unsupported");
        Contracts.ToolContract tool=new Contracts.ToolContract(
                "tool.test","1.0.0","1.0.0",Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),
                Collections.emptyList(),Collections.emptyList(),Collections.singletonList("permission.vibrate"),"entry.test");
        registry.publish(new Contracts.ToolBundle(tool,Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),
                Collections.emptyList(),Collections.singletonList(permission)));
        AndroidPermissionStateProvider permissions=new AndroidPermissionStateProvider(this,registry);
        check(permissions.permissionState("permission.vibrate")==StageAContracts.Availability.AVAILABLE);
        check(permissions.permissionState("permission.missing")==StageAContracts.Availability.UNAVAILABLE);
        AndroidResourcePolicyProvider resources=AndroidResourcePolicyProvider.stageAIdle(this);
        SafetyContracts.ResourceBudget budget=resources.budgetFor("tool.test");
        SafetyContracts.ResourceSample sample=resources.sampleFor("tool.test");
        check(budget.memoryUnits()==10000 && sample.workUnits()==0 && sample.concurrentOperations()==0);
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
        StageAContracts.SafeUiModel model=SafeUiPolicy.modelFor(SafetyContracts.RecoveryState.QUARANTINED);
        final int[] calls={0};
        View view=AndroidSafeUi.render(this,model,"Pemeriksaan keselamatan diperlukan.",new AndroidSafeUi.Actions(){
            public String verifyIntegrity(){calls[0]++; return "Integritas diperiksa.";}
            public String retryBootstrap(){calls[0]++; return "Bootstrap dijadwalkan ulang.";}
            public String enterReadOnly(){calls[0]++; return "Mode baca-saja aktif.";}
            public String exportSanitizedDiagnostics(){calls[0]++; return "Diagnostik aman siap.";}
            public boolean canRestoreKnownGood(){return false;}
            public String restoreKnownGood(){calls[0]++; return "Pemulihan tidak tersedia.";}
            public boolean canQuarantine(){return false;}
            public String quarantine(){calls[0]++; return "Karantina tidak tersedia.";}
        });
        check(view!=null && model.visible() && model.restricted());
        setContentView(view);
        Log.i(TAG,"STAGE_A_HOST_SAFE_UI_PASS");
    }
    private static void check(boolean v){ if(!v) throw new AssertionError(); }
}
