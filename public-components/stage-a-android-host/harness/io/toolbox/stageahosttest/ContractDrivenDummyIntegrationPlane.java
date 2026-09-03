package io.toolbox.stageahosttest;

import io.toolbox.contracts.runtime.ProductRegistry;
import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;
import io.toolbox.stagea.android.AndroidAtomicStateStore;
import io.toolbox.stagea.android.AndroidSafeUi;
import io.toolbox.stagea.android.AndroidStageAHost;

/**
 * Independent Public surrogate derived only from the safe receiver contract.
 * It intentionally knows no Private class name, path, mapping, baseline state, or
 * receiver implementation. It proves only the ordering and socket invariants that
 * the canonical contract exposes.
 */
final class ContractDrivenDummyIntegrationPlane {
    private enum Phase { EMPTY, PROVIDER_BOUND, BOOTSTRAPPED, KERNEL_READY, UI_ROUTED }

    private Phase phase = Phase.EMPTY;
    private AndroidStageAHost host;
    private ProductRegistry registryIdentity;

    void bindProvider(AndroidStageAHost provider) {
        require(phase == Phase.EMPTY, "provider may bind exactly once");
        require(provider != null, "provider required");
        host = provider;
        registryIdentity = provider.productRegistry();
        require(registryIdentity != null, "registry socket required");
        phase = Phase.PROVIDER_BOUND;
    }

    SafetyContracts.RecoveryState bootstrap() {
        require(phase == Phase.PROVIDER_BOUND, "bootstrap must follow provider bind");
        SafetyContracts.RecoveryState state = host.bootstrap();
        require(host.productRegistry() == registryIdentity, "registry identity drifted during bootstrap");
        require(host.durableStateStore() != null, "durable-state socket required");
        require(host.diagnostics() != null, "diagnostic socket required");
        require(host.safeUiActions() != null, "safe-ui action socket required");
        phase = Phase.BOOTSTRAPPED;
        return state;
    }

    void markKernelReady() {
        require(phase == Phase.BOOTSTRAPPED, "kernel may become ready only after bootstrap");
        require(host.productRegistry() == registryIdentity, "kernel route must use shared registry instance");
        phase = Phase.KERNEL_READY;
    }

    boolean routeRestrictedBeforeNormal() {
        require(phase == Phase.KERNEL_READY, "UI routing must follow kernel-ready decision point");
        StageAContracts.SafeUiModel model = host.safeUiModel();
        boolean restricted = model.visible() && model.restricted();
        if (restricted) {
            AndroidSafeUi.Actions actions = host.safeUiActions();
            require(actions != null, "restricted UI requires production actions");
        }
        phase = Phase.UI_ROUTED;
        return restricted;
    }

    void verifyClosed() {
        require(phase == Phase.UI_ROUTED, "surrogate plane lifecycle incomplete");
        require(host.productRegistry() == registryIdentity, "registry identity changed after full route");
        AndroidAtomicStateStore store = host.durableStateStore();
        require(store != null, "durable state delegate unavailable");
        require(host.health() != null, "resource/health socket unavailable");
    }

    ProductRegistry registry() {
        require(phase != Phase.EMPTY, "registry before provider bind");
        return registryIdentity;
    }

    AndroidStageAHost host() {
        require(phase != Phase.EMPTY, "host before provider bind");
        return host;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("stable integration plane contract mismatch: " + message);
    }
}
