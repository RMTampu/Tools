package com.toolbox.tools.repair;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.RecoveryManager;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RepairSessionManager {
    public static final int MAX_HISTORY = 32;

    private final ProjectManager projectManager;
    private final RecoveryManager recoveryManager;
    private final RepairPlanValidator validator = new RepairPlanValidator();
    private final ProjectValidator projectValidator = new ProjectValidator();
    private final Deque<RepairHistoryEntry> history = new ArrayDeque<>();
    private final List<RepairDiagnostic> diagnostics = new ArrayList<>();

    private RepairPlan activePlan;
    private RepairPhase phase = RepairPhase.NEW;
    private long preActivationRevision = -1;
    private long activatedRevision = -1;

    public RepairSessionManager(
            ProjectManager projectManager,
            RecoveryManager recoveryManager
    ) {
        this.projectManager = Objects.requireNonNull(
                projectManager,
                "projectManager"
        );
        this.recoveryManager = Objects.requireNonNull(
                recoveryManager,
                "recoveryManager"
        );
    }

    public synchronized RepairValidationResult stage(RepairPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (phase == RepairPhase.ACTIVATED) {
            throw new IllegalStateException("repair activation still active");
        }
        RepairValidationResult validation =
                validator.validate(projectManager, plan);
        if (!validation.isPass()) {
            diagnostics.addAll(validation.diagnostics());
            phase = RepairPhase.FAILED_SAFE;
            pushHistory(plan.planId(), phase, projectManager.savedRevision());
            return validation;
        }
        activePlan = plan;
        phase = RepairPhase.STAGED;
        preActivationRevision = -1;
        activatedRevision = -1;
        pushHistory(plan.planId(), phase, projectManager.savedRevision());
        return validation;
    }

    public synchronized ProjectState activate() throws IOException {
        requirePhase(RepairPhase.STAGED);
        preActivationRevision = projectManager.savedRevision();
        projectManager.captureFinalRecoverySnapshot();

        try {
            projectManager.applyResourceTransaction(
                    activePlan.upserts(),
                    activePlan.deletes()
            );
            ProjectState committed = projectManager.save();
            activatedRevision = committed.revision();
            phase = RepairPhase.ACTIVATED;
            pushHistory(
                    activePlan.planId(),
                    phase,
                    activatedRevision
            );
            return committed;
        } catch (IOException | RuntimeException error) {
            recoveryManager.markRecoveryRequired();
            diagnostics.add(new RepairDiagnostic(
                    "repair.activate.failed",
                    error.getMessage() == null
                            ? "Aktivasi repair gagal"
                            : error.getMessage()
            ));
            phase = RepairPhase.FAILED_SAFE;
            pushHistory(
                    activePlan.planId(),
                    phase,
                    projectManager.savedRevision()
            );
            if (projectManager.savedRevision() != preActivationRevision) {
                rollbackInternal();
            }
            if (error instanceof IOException) throw (IOException) error;
            throw error;
        }
    }

    public synchronized boolean verifyOrRollback() throws IOException {
        requirePhase(RepairPhase.ACTIVATED);
        ProjectState current = projectManager.current();

        ProjectValidationResult projectValidation =
                projectValidator.validate(current);
        if (!projectValidation.isPass()) {
            diagnostics.add(new RepairDiagnostic(
                    "repair.verify.project",
                    projectValidation.message()
            ));
            rollbackInternal();
            return false;
        }

        for (Map.Entry<String, String> entry : activePlan.upserts().entrySet()) {
            if (!entry.getValue().equals(
                    current.resources().get(entry.getKey())
            )) {
                diagnostics.add(new RepairDiagnostic(
                        "repair.verify.upsert",
                        "Expected repair value tidak aktif"
                ));
                rollbackInternal();
                return false;
            }
        }
        for (String id : activePlan.deletes()) {
            if (current.resources().containsKey(id)) {
                diagnostics.add(new RepairDiagnostic(
                        "repair.verify.delete",
                        "Expected deleted resource masih aktif"
                ));
                rollbackInternal();
                return false;
            }
        }

        if (current.revision() != activatedRevision) {
            diagnostics.add(new RepairDiagnostic(
                    "repair.verify.revision",
                    "Activated revision berubah sebelum verify"
            ));
            rollbackInternal();
            return false;
        }

        phase = RepairPhase.VERIFIED;
        recoveryManager.clearRecoveryRequired();
        pushHistory(activePlan.planId(), phase, current.revision());
        return true;
    }

    public synchronized ProjectState rollback() throws IOException {
        if (phase == RepairPhase.ROLLED_BACK) {
            return projectManager.current();
        }
        if (phase != RepairPhase.ACTIVATED
                && phase != RepairPhase.FAILED_SAFE) {
            throw new IllegalStateException("repair rollback unavailable");
        }
        return rollbackInternal();
    }

    private ProjectState rollbackInternal() throws IOException {
        if (preActivationRevision <= 0) {
            recoveryManager.markRecoveryRequired();
            phase = RepairPhase.FAILED_SAFE;
            throw new IOException("repair recovery point unavailable");
        }
        try {
            ProjectState restored =
                    projectManager.restoreRevision(preActivationRevision);
            phase = RepairPhase.ROLLED_BACK;
            recoveryManager.clearRecoveryRequired();
            pushHistory(
                    activePlan == null ? "repair.unknown" : activePlan.planId(),
                    phase,
                    restored.revision()
            );
            return restored;
        } catch (IOException error) {
            recoveryManager.markRecoveryRequired();
            phase = RepairPhase.FAILED_SAFE;
            diagnostics.add(new RepairDiagnostic(
                    "repair.rollback.failed",
                    error.getMessage() == null
                            ? "Rollback repair gagal"
                            : error.getMessage()
            ));
            throw error;
        }
    }

    public synchronized RepairPhase phase() { return phase; }
    public synchronized RepairPlan activePlan() { return activePlan; }
    public synchronized long preActivationRevision() {
        return preActivationRevision;
    }
    public synchronized long activatedRevision() {
        return activatedRevision;
    }

    public synchronized List<RepairHistoryEntry> history() {
        return Collections.unmodifiableList(
                new ArrayList<>(history)
        );
    }

    public synchronized List<RepairDiagnostic> diagnostics() {
        return Collections.unmodifiableList(
                new ArrayList<>(diagnostics)
        );
    }

    private void requirePhase(RepairPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "repair transition invalid: "
                            + phase + " -> " + expected
            );
        }
    }

    private void pushHistory(
            String planId,
            RepairPhase next,
            long revision
    ) {
        history.addLast(new RepairHistoryEntry(
                planId,
                next,
                revision
        ));
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }
}
