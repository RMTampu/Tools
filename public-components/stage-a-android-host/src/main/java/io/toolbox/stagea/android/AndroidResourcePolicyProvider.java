package io.toolbox.stagea.android;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

public final class AndroidResourcePolicyProvider implements StageAContracts.ResourcePolicyProvider {
    public interface WorkloadProbe {
        int measuredWorkCapacity();
        int measuredConcurrentCapacity();
        int currentWorkUnits();
        int currentConcurrentOperations();
    }

    private final ActivityManager activityManager;
    private final WorkloadProbe workload;

    public AndroidResourcePolicyProvider(Context context, WorkloadProbe workload) {
        if (context == null) throw new NullPointerException("context");
        if (workload == null) throw new NullPointerException("workload");
        Object service = context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (!(service instanceof ActivityManager)) {
            throw new StageAContracts.StageAException("resource.measurement.unavailable", "ActivityManager unavailable");
        }
        this.activityManager = (ActivityManager) service;
        this.workload = workload;
    }

    public static AndroidResourcePolicyProvider stageAIdle(Context context) {
        return new AndroidResourcePolicyProvider(context, new WorkloadProbe() {
            @Override public int measuredWorkCapacity() { return 1; }
            @Override public int measuredConcurrentCapacity() { return 1; }
            @Override public int currentWorkUnits() { return 0; }
            @Override public int currentConcurrentOperations() { return 0; }
        });
    }

    @Override
    public SafetyContracts.ResourceBudget budgetFor(String providerToolId) {
        return new SafetyContracts.ResourceBudget(
                NormalizedResourceMath.NORMALIZED_BUDGET,
                NormalizedResourceMath.requireBudget(workload.measuredWorkCapacity(), "measuredWorkCapacity"),
                NormalizedResourceMath.requireBudget(workload.measuredConcurrentCapacity(), "measuredConcurrentCapacity")
        );
    }

    @Override
    public SafetyContracts.ResourceSample sampleFor(String providerToolId) {
        int memoryClassMiB = activityManager.getMemoryClass();
        if (memoryClassMiB <= 0) {
            throw new StageAContracts.StageAException("resource.measurement.invalid", "Invalid Android memory class");
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long pssKiB = Debug.getPss();
        long capacityKiB = (long) memoryClassMiB * 1024L;
        int memoryUnits = NormalizedResourceMath.normalizedUsage(pssKiB, capacityKiB, memoryInfo.lowMemory);
        return new SafetyContracts.ResourceSample(
                memoryUnits,
                NormalizedResourceMath.requireSample(workload.currentWorkUnits(), "currentWorkUnits"),
                NormalizedResourceMath.requireSample(workload.currentConcurrentOperations(), "currentConcurrentOperations")
        );
    }
}
