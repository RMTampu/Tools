package com.toolbox.tools.delivery;

import com.toolbox.tools.core.MemoryRuntimeStateStore;
import com.toolbox.tools.core.RuntimeStateStore;

import java.util.Objects;

public final class PatchTransactionJournal {
    public enum Phase {
        IDLE,
        PREPARED,
        SNAPSHOT_READY,
        MUTATING,
        VERIFYING,
        HEALTH_CHECK,
        COMMITTING,
        ROLLING_BACK,
        FAILED_SAFE
    }

    private static final String KEY_PHASE = "patch.journal.phase";
    private static final String KEY_PATCH = "patch.journal.patch";
    private static final String KEY_BASE = "patch.journal.base";
    private static final String KEY_TARGET = "patch.journal.target";
    private static final String KEY_PAYLOAD = "patch.journal.payload";

    private final RuntimeStateStore state;

    public PatchTransactionJournal() {
        this(new MemoryRuntimeStateStore());
    }

    public PatchTransactionJournal(RuntimeStateStore state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public synchronized void begin(
            PatchManifest manifest,
            PatchPayload payload
    ) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(payload, "payload");
        state.put(KEY_PATCH, manifest.patchId());
        state.put(KEY_BASE, Long.toString(manifest.baseRevision()));
        state.put(KEY_TARGET, Long.toString(manifest.targetRevision()));
        state.put(KEY_PAYLOAD, payload.sha256());
        phase(Phase.PREPARED);
    }

    public synchronized void phase(Phase phase) {
        state.put(KEY_PHASE, Objects.requireNonNull(phase, "phase").name());
    }

    public synchronized Phase phase() {
        String raw = state.get(KEY_PHASE);
        if (raw == null) return Phase.IDLE;
        try {
            return Phase.valueOf(raw);
        } catch (IllegalArgumentException error) {
            return Phase.FAILED_SAFE;
        }
    }

    public synchronized boolean active() {
        return phase() != Phase.IDLE;
    }

    public synchronized long baseRevision() {
        return parseLong(state.get(KEY_BASE));
    }

    public synchronized long targetRevision() {
        return parseLong(state.get(KEY_TARGET));
    }

    public synchronized String patchId() {
        String value = state.get(KEY_PATCH);
        return value == null ? "" : value;
    }

    public synchronized String payloadSha256() {
        String value = state.get(KEY_PAYLOAD);
        return value == null ? "" : value;
    }

    public synchronized void clear() {
        state.remove(KEY_PHASE);
        state.remove(KEY_PATCH);
        state.remove(KEY_BASE);
        state.remove(KEY_TARGET);
        state.remove(KEY_PAYLOAD);
    }

    private static long parseLong(String value) {
        if (value == null) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
