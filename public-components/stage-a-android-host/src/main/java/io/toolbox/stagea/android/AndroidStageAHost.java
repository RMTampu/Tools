package io.toolbox.stagea.android;

import android.content.Context;

import io.toolbox.contracts.runtime.Contracts;
import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.ResourceGuard;
import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.DiagnosticMapper;
import io.toolbox.stagea.ExecutionGuard;
import io.toolbox.stagea.HealthAggregator;
import io.toolbox.stagea.RecoveryCoordinator;
import io.toolbox.stagea.SafeUiPolicy;
import io.toolbox.stagea.StageAContracts;

public final class AndroidStageAHost {
    public interface CapabilityProbe {
        StageAContracts.Availability availability(String capabilityId);
    }

    private final ProductRegistry registry;
    private final AndroidAtomicStateStore durableStore;
    private final AndroidRecoveryStateStore recoveryStore;
    private final RecoveryCoordinator recovery;
    private final DiagnosticBuffer diagnostics;
    private final DiagnosticMapper diagnosticMapper;
    private final AndroidResourcePolicyProvider resources;
    private final ExecutionGuard executionGuard;

    private AndroidStageAHost(Context context, CapabilityProbe capabilityProbe,
            AndroidResourcePolicyProvider resourcePolicy) {
        Context appContext = context.getApplicationContext();
        this.registry = new ProductRegistry();
        this.durableStore = new AndroidAtomicStateStore(appContext);
        this.recoveryStore = new AndroidRecoveryStateStore(durableStore);
        this.recovery = new RecoveryCoordinator(recoveryStore);
        this.diagnostics = new DiagnosticBuffer(128);
        this.diagnosticMapper = new DiagnosticMapper();
        this.resources = resourcePolicy != null ? resourcePolicy : AndroidResourcePolicyProvider.stageAIdle(appContext);
        StageAContracts.CapabilityStateProvider capabilityProvider = capabilityId -> {
            if (!registry.capability(capabilityId).isPresent()) return StageAContracts.Availability.UNAVAILABLE;
            StageAContracts.Availability value = capabilityProbe == null ? null : capabilityProbe.availability(capabilityId);
            return value == null ? StageAContracts.Availability.UNAVAILABLE : value;
        };
        this.executionGuard = new ExecutionGuard(
                registry,
                new AndroidPermissionStateProvider(appContext, registry),
                capabilityProvider,
                resources,
                recovery
        );
    }

    public static AndroidStageAHost createStageA(Context context) {
        return new AndroidStageAHost(context, capabilityId -> StageAContracts.Availability.UNAVAILABLE, null);
    }

    public static AndroidStageAHost create(Context context, CapabilityProbe capabilityProbe,
            AndroidResourcePolicyProvider resourcePolicy) {
        if (context == null) throw new NullPointerException("context");
        return new AndroidStageAHost(context, capabilityProbe, resourcePolicy);
    }

    public SafetyContracts.RecoveryState bootstrap() {
        SafetyContracts.RecoveryState state = recovery.bootstrap();
        if (state == SafetyContracts.RecoveryState.QUARANTINED) {
            diagnostics.record(diagnosticMapper.event(
                    "stage.a.host", SafetyContracts.Severity.FATAL,
                    "recovery.bootstrap.quarantined", "diagnostic.recovery.quarantined"));
        }
        return state;
    }

    public ProductRegistry.RegistrySnapshot publish(Contracts.ToolBundle bundle) {
        try {
            return registry.publish(bundle);
        } catch (RuntimeException failure) {
            diagnostics.record(diagnosticMapper.fromFailure("stage.a.registry", failure));
            throw failure;
        }
    }

    public StageAContracts.AdmissionDecision admit(String actionId) {
        return executionGuard.evaluate(actionId);
    }

    public StageAContracts.SafeUiModel safeUiModel() {
        return SafeUiPolicy.modelFor(recovery.state());
    }

    public AndroidSafeUi.Actions safeUiActions() {
        return new AndroidSafeUiActions(this);
    }

    public StageAContracts.HealthSnapshot health() {
        SafetyContracts.ResourceBudget budget = resources.budgetFor("stage.a.host");
        SafetyContracts.ResourceSample sample = resources.sampleFor("stage.a.host");
        SafetyContracts.GuardDecision guard = ResourceGuard.evaluate(budget, sample);
        return HealthAggregator.aggregate(registry.snapshot(), diagnostics, recovery.state(), guard);
    }

    public ProductRegistry.RegistrySnapshot registrySnapshot() { return registry.snapshot(); }
    public AndroidAtomicStateStore durableStateStore() { return durableStore; }
    public RecoveryCoordinator recoveryCoordinator() { return recovery; }
    public DiagnosticBuffer diagnostics() { return diagnostics; }
}
