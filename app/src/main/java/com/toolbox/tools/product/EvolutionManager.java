package com.toolbox.tools.product;

import com.toolbox.tools.delivery.PatchApplyResult;
import com.toolbox.tools.delivery.PatchManifest;
import com.toolbox.tools.delivery.PatchPayload;
import com.toolbox.tools.delivery.RemotePatchVerifier;
import com.toolbox.tools.delivery.RemoteVerificationProof;
import com.toolbox.tools.delivery.SafePatchManager;
import java.util.Objects;

public final class EvolutionManager {
    public enum State {
        IDLE,
        STAGED,
        VALIDATED,
        PREVIEW_READY,
        APPLYING,
        VERIFIED,
        ROLLED_BACK,
        FAILED_SAFE
    }

    private final SafePatchManager patches;
    private final RemotePatchVerifier verifier;
    private State state = State.IDLE;
    private PatchManifest manifest;
    private PatchPayload payload;
    private RemoteVerificationProof proof;
    private long baseRevision;

    public EvolutionManager(
            SafePatchManager patches,
            RemotePatchVerifier verifier
    ) {
        this.patches = Objects.requireNonNull(patches, "patches");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    public synchronized void stage(
            PatchManifest manifest,
            PatchPayload payload,
            RemoteVerificationProof proof
    ) {
        if (manifest == null || payload == null || proof == null) {
            throw new IllegalArgumentException("paket evolusi tidak lengkap");
        }
        this.manifest = manifest;
        this.payload = payload;
        this.proof = proof;
        this.baseRevision = manifest.baseRevision();
        state = State.STAGED;
    }

    public synchronized boolean validate() {
        ensure(State.STAGED);
        boolean valid = manifest.payloadSha256().equals(payload.sha256())
                && verifier.verify(manifest, payload, proof);
        state = valid ? State.VALIDATED : State.FAILED_SAFE;
        return valid;
    }

    public synchronized String preview() {
        ensure(State.VALIDATED);
        state = State.PREVIEW_READY;
        return "Paket " + manifest.patchId()
                + " • revisi " + manifest.baseRevision()
                + " → " + manifest.targetRevision()
                + " • perubahan " + payload.upserts().size()
                + " • hapus " + payload.deletes().size();
    }

    public synchronized PatchApplyResult apply() {
        ensure(State.PREVIEW_READY);
        state = State.APPLYING;
        PatchApplyResult result = patches.apply(manifest, payload, proof);
        if (result.state() == PatchApplyResult.State.APPLIED) {
            state = State.VERIFIED;
        } else if (result.state() == PatchApplyResult.State.RESTORED) {
            state = State.ROLLED_BACK;
        } else {
            state = State.FAILED_SAFE;
        }
        return result;
    }

    public synchronized PatchApplyResult rollback() {
        if (baseRevision <= 0) {
            throw new IllegalStateException("revisi dasar belum tersedia");
        }
        PatchApplyResult result = patches.restore(baseRevision);
        state = result.state() == PatchApplyResult.State.RESTORED
                ? State.ROLLED_BACK
                : State.FAILED_SAFE;
        return result;
    }

    public synchronized State state() { return state; }

    public synchronized void reset() {
        state = State.IDLE;
        manifest = null;
        payload = null;
        proof = null;
        baseRevision = 0;
    }

    private void ensure(State expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "state evolusi tidak valid: " + state + " != " + expected
            );
        }
    }
}
