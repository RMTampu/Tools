package com.toolbox.tools.live;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.StableId;
import com.toolbox.tools.repair.RepairPlan;
import com.toolbox.tools.repair.RepairSessionManager;
import com.toolbox.tools.repair.RepairValidationResult;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LiveSessionManager {
    public static final int MAX_CHANGES = 64;
    public static final int MAX_HISTORY = 32;

    private final ProjectManager projectManager;
    private final RepairSessionManager repairSessionManager;
    private final SelfEditPolicy selfEditPolicy;
    private final TargetDescriptor selfTarget;
    private final Map<String, LiveChange> changes = new LinkedHashMap<>();
    private final Deque<LiveHistoryEntry> history = new ArrayDeque<>();

    private String sessionId;
    private String targetId;
    private long baseRevision = -1;
    private LiveSessionState state = LiveSessionState.CLOSED;

    public LiveSessionManager(
            ProjectManager projectManager,
            RepairSessionManager repairSessionManager,
            SelfEditPolicy selfEditPolicy,
            TargetDescriptor selfTarget
    ) {
        this.projectManager = Objects.requireNonNull(
                projectManager,
                "projectManager"
        );
        this.repairSessionManager = Objects.requireNonNull(
                repairSessionManager,
                "repairSessionManager"
        );
        this.selfEditPolicy = Objects.requireNonNull(
                selfEditPolicy,
                "selfEditPolicy"
        );
        this.selfTarget = Objects.requireNonNull(
                selfTarget,
                "selfTarget"
        );
    }

    public synchronized void open(
            String sessionId,
            TargetDescriptor target,
            CapabilityScanResult scan
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(scan, "scan");

        if (!target.targetId().equals(scan.targetId())) {
            throw new IllegalArgumentException("LIVE_TARGET_SCAN_MISMATCH");
        }
        if (!target.installed() || !scan.liveAvailable()) {
            throw new IllegalStateException("LIVE_RUNTIME_UNAVAILABLE");
        }
        if (!target.selfTarget()
                || !target.targetId().equals(selfTarget.targetId())
                || target.editDoor() != EditDoor.DECLARATIVE) {
            throw new IllegalStateException("LIVE_EDIT_BRIDGE_UNAVAILABLE");
        }
        if (projectManager.hasUnsavedChanges()
                || projectManager.savedRevision() <= 0) {
            throw new IllegalStateException(
                    "LIVE_PROJECT_REQUIRES_SAVED_REVISION"
            );
        }

        this.sessionId = StableId.require(sessionId, "sessionId");
        this.targetId = target.targetId();
        this.baseRevision = projectManager.savedRevision();
        this.changes.clear();
        this.state = LiveSessionState.OPEN;
    }

    public synchronized void queue(LiveChange change) {
        Objects.requireNonNull(change, "change");
        if (state != LiveSessionState.OPEN
                && state != LiveSessionState.DIRTY
                && state != LiveSessionState.APPLIED) {
            throw new IllegalStateException("LIVE_SESSION_NOT_EDITABLE");
        }

        selfEditPolicy.requireEditable(change.resourceId());

        if (!changes.containsKey(change.resourceId())
                && changes.size() >= MAX_CHANGES) {
            throw new IllegalStateException("LIVE_CHANGE_BUDGET_EXCEEDED");
        }

        changes.put(change.resourceId(), change);
        state = LiveSessionState.DIRTY;
    }

    public synchronized LiveCompareResult compare() {
        requireOpenLikeState();
        return new LiveCompareResult(
                new ArrayList<>(changes.values())
        );
    }

    public synchronized LiveApplyResult terapkan() {
        requireOpenLikeState();

        if (state == LiveSessionState.APPLIED
                || (state == LiveSessionState.OPEN && changes.isEmpty())) {
            return new LiveApplyResult(
                    true,
                    state,
                    projectManager.savedRevision(),
                    "NO_CHANGE"
            );
        }

        if (state != LiveSessionState.DIRTY) {
            return new LiveApplyResult(
                    false,
                    state,
                    projectManager.savedRevision(),
                    "LIVE_NOT_DIRTY"
            );
        }

        LiveCompareResult compare = compare();

        if (projectManager.savedRevision() != baseRevision
                || projectManager.hasUnsavedChanges()) {
            state = LiveSessionState.CONFLICT;
            pushHistory(state, projectManager.savedRevision(), compare.checksum());
            return new LiveApplyResult(
                    false,
                    state,
                    projectManager.savedRevision(),
                    "LIVE_BASE_REVISION_CONFLICT"
            );
        }

        Map<String, String> upserts = new LinkedHashMap<>();
        Set<String> deletes = new LinkedHashSet<>();
        for (LiveChange change : compare.changes()) {
            selfEditPolicy.requireEditable(change.resourceId());
            if (change.operation() == LiveChangeOperation.DELETE) {
                deletes.add(change.resourceId());
            } else {
                upserts.put(change.resourceId(), change.payload());
            }
        }

        String planId = "repair.live." + sessionId;
        RepairPlan plan = new RepairPlan(
                planId,
                projectManager.current().projectId(),
                baseRevision,
                upserts,
                deletes
        );

        try {
            RepairValidationResult staged =
                    repairSessionManager.stage(plan);
            if (!staged.isPass()) {
                state = LiveSessionState.FAILED_SAFE;
                pushHistory(
                        state,
                        projectManager.savedRevision(),
                        compare.checksum()
                );
                return new LiveApplyResult(
                        false,
                        state,
                        projectManager.savedRevision(),
                        staged.diagnostics().isEmpty()
                                ? "LIVE_STAGE_FAILED"
                                : staged.diagnostics().get(0).code()
                );
            }

            repairSessionManager.activate();
            boolean verified =
                    repairSessionManager.verifyOrRollback();

            if (!verified) {
                state = LiveSessionState.FAILED_SAFE;
                pushHistory(
                        state,
                        projectManager.savedRevision(),
                        compare.checksum()
                );
                return new LiveApplyResult(
                        false,
                        state,
                        projectManager.savedRevision(),
                        "LIVE_VERIFY_ROLLED_BACK"
                );
            }

            baseRevision = projectManager.savedRevision();
            changes.clear();
            state = LiveSessionState.APPLIED;
            pushHistory(
                    state,
                    baseRevision,
                    compare.checksum()
            );
            return new LiveApplyResult(
                    true,
                    state,
                    baseRevision,
                    "TERAPKAN_PASS"
            );
        } catch (IOException | RuntimeException error) {
            state = LiveSessionState.FAILED_SAFE;
            pushHistory(
                    state,
                    projectManager.savedRevision(),
                    compare.checksum()
            );
            return new LiveApplyResult(
                    false,
                    state,
                    projectManager.savedRevision(),
                    error.getMessage() == null
                            ? "LIVE_APPLY_FAILED"
                            : error.getMessage()
            );
        }
    }

    public synchronized void close() {
        changes.clear();
        sessionId = null;
        targetId = null;
        baseRevision = -1;
        state = LiveSessionState.CLOSED;
    }

    public synchronized LiveSessionState state() { return state; }
    public synchronized String sessionId() { return sessionId; }
    public synchronized String targetId() { return targetId; }
    public synchronized long baseRevision() { return baseRevision; }
    public synchronized int queuedChangeCount() { return changes.size(); }

    public synchronized List<LiveHistoryEntry> history() {
        return Collections.unmodifiableList(
                new ArrayList<>(history)
        );
    }

    public SelfEditPolicy selfEditPolicy() {
        return selfEditPolicy;
    }

    private void requireOpenLikeState() {
        if (state == LiveSessionState.CLOSED
                || state == LiveSessionState.CONFLICT
                || state == LiveSessionState.FAILED_SAFE) {
            throw new IllegalStateException("LIVE_SESSION_UNAVAILABLE");
        }
    }

    private void pushHistory(
            LiveSessionState next,
            long revision,
            String checksum
    ) {
        history.addLast(new LiveHistoryEntry(
                sessionId == null ? "live.closed" : sessionId,
                next,
                revision,
                checksum
        ));
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }
}
