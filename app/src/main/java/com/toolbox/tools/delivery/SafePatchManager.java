package com.toolbox.tools.delivery;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.RecoveryManager;
import com.toolbox.tools.core.RuntimeStateStore;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class SafePatchManager {
    private final ProjectManager projectManager;
    private final RecoveryManager recoveryManager;
    private final RemotePatchVerifier remoteVerifier;
    private final PatchActivationHook activationHook;
    private final ProjectValidator projectValidator = new ProjectValidator();
    private final PatchTransactionJournal journal;
    private final String hostPackageName;
    private final int hostVersionCode;
    private final java.util.Set<String> hostCapabilities;
    private String runtimeParentApkSha256;
    private String runtimeRollbackBaselineApkSha256;
    private PatchHealthGate healthGate = PatchHealthGate.PROJECT_ONLY;

    public SafePatchManager(
            ProjectManager projectManager,
            RecoveryManager recoveryManager,
            RemotePatchVerifier remoteVerifier
    ){
        this(
                projectManager,
                recoveryManager,
                remoteVerifier,
                PatchActivationHook.NO_OP,
                recoveryManager.stateStore(),
                com.toolbox.tools.BuildConfig.APPLICATION_ID,
                com.toolbox.tools.BuildConfig.VERSION_CODE,
                new java.util.LinkedHashSet<>(
                        java.util.Arrays.asList(
                                "ui",
                                "logic",
                                "data",
                                "binding",
                                "asset"
                        )
                )
        );
    }

    public SafePatchManager(
            ProjectManager projectManager,
            RecoveryManager recoveryManager,
            RemotePatchVerifier remoteVerifier,
            PatchActivationHook activationHook
    ){
        this(
                projectManager,
                recoveryManager,
                remoteVerifier,
                activationHook,
                recoveryManager.stateStore(),
                com.toolbox.tools.BuildConfig.APPLICATION_ID,
                com.toolbox.tools.BuildConfig.VERSION_CODE,
                new java.util.LinkedHashSet<>(
                        java.util.Arrays.asList(
                                "ui",
                                "logic",
                                "data",
                                "binding",
                                "asset"
                        )
                )
        );
    }

    public SafePatchManager(
            ProjectManager projectManager,
            RecoveryManager recoveryManager,
            RemotePatchVerifier remoteVerifier,
            PatchActivationHook activationHook,
            RuntimeStateStore runtimeState
    ){
        this(
                projectManager,
                recoveryManager,
                remoteVerifier,
                activationHook,
                runtimeState,
                com.toolbox.tools.BuildConfig.APPLICATION_ID,
                com.toolbox.tools.BuildConfig.VERSION_CODE,
                new java.util.LinkedHashSet<>(
                        java.util.Arrays.asList(
                                "ui",
                                "logic",
                                "data",
                                "binding",
                                "asset"
                        )
                )
        );
    }

    public SafePatchManager(
            ProjectManager projectManager,
            RecoveryManager recoveryManager,
            RemotePatchVerifier remoteVerifier,
            PatchActivationHook activationHook,
            RuntimeStateStore runtimeState,
            String hostPackageName,
            int hostVersionCode,
            java.util.Set<String> hostCapabilities
    ){
        this.projectManager = Objects.requireNonNull(
                projectManager,
                "projectManager"
        );
        this.recoveryManager = Objects.requireNonNull(
                recoveryManager,
                "recoveryManager"
        );
        this.remoteVerifier = Objects.requireNonNull(
                remoteVerifier,
                "remoteVerifier"
        );
        this.activationHook = Objects.requireNonNull(
                activationHook,
                "activationHook"
        );
        this.journal = new PatchTransactionJournal(
                Objects.requireNonNull(runtimeState, "runtimeState")
        );
        this.hostPackageName = Objects.requireNonNull(
                hostPackageName,
                "hostPackageName"
        );
        if (hostVersionCode < 1) {
            throw new IllegalArgumentException(
                    "hostVersionCode invalid"
            );
        }
        this.hostVersionCode = hostVersionCode;
        this.hostCapabilities = java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(
                        Objects.requireNonNull(
                                hostCapabilities,
                                "hostCapabilities"
                        )
                )
        );
    }

    public synchronized void bindRuntimeApkIdentity(
            String currentSignedApkSha256,
            String rollbackBaselineApkSha256
    ) {
        requireSha256(
                currentSignedApkSha256,
                "currentSignedApkSha256"
        );
        requireSha256(
                rollbackBaselineApkSha256,
                "rollbackBaselineApkSha256"
        );
        this.runtimeParentApkSha256 =
                currentSignedApkSha256;
        this.runtimeRollbackBaselineApkSha256 =
                rollbackBaselineApkSha256;
    }

    public synchronized boolean runtimeApkIdentityBound() {
        return runtimeParentApkSha256 != null
                && runtimeRollbackBaselineApkSha256 != null;
    }

    public synchronized String runtimeParentApkSha256() {
        return runtimeParentApkSha256;
    }

    public synchronized String runtimeRollbackBaselineApkSha256() {
        return runtimeRollbackBaselineApkSha256;
    }

    public synchronized void setHealthGate(PatchHealthGate healthGate) {
        this.healthGate = Objects.requireNonNull(
                healthGate,
                "healthGate"
        );
    }

    public synchronized PatchTransactionJournal journal() {
        return journal;
    }

    public synchronized void bootstrap() throws IOException {
        if (!journal.active()) return;

        long baseRevision = journal.baseRevision();
        recoveryManager.markRecoveryRequired(
                "INTERRUPTED_PATCH_TRANSACTION",
                journal.patchId()
        );
        journal.phase(PatchTransactionJournal.Phase.ROLLING_BACK);

        if (baseRevision <= 0) {
            journal.phase(PatchTransactionJournal.Phase.FAILED_SAFE);
            throw new IOException("patch journal base revision missing");
        }

        try {
            ProjectState restored =
                    projectManager.restoreRevision(baseRevision);
            activationHook.onActivated(restored);
            if (!projectValidator.validate(restored).isPass()
                    || !healthGate.isHealthy(restored)) {
                throw new IOException(
                        "restored patch baseline failed health"
                );
            }
            journal.clear();
            recoveryManager.clearRecoveryRequired();
        } catch (Exception error) {
            journal.phase(PatchTransactionJournal.Phase.FAILED_SAFE);
            recoveryManager.markRecoveryRequired(
                    "PATCH_BOOTSTRAP_ROLLBACK_FAILED",
                    journal.patchId()
            );
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(
                    "patch bootstrap rollback failed",
                    error
            );
        }
    }

    public synchronized PatchDryRunResult dryRun(
            PatchManifest manifest,
            PatchPayload payload,
            RemoteVerificationProof proof
    ) {
        String rejected = validateInputs(manifest, payload, proof);
        if (rejected != null) {
            return new PatchDryRunResult(
                    false,
                    rejected,
                    null
            );
        }

        try {
            ProjectState candidate = projectManager.current();
            for (Map.Entry<String, String> entry
                    : payload.upserts().entrySet()) {
                candidate = candidate.withResource(
                        entry.getKey(),
                        entry.getValue()
                );
            }
            for (String id : payload.deletes()) {
                candidate = candidate.withoutResource(id);
            }
            candidate = candidate.withRevision(
                    manifest.targetRevision()
            );

            ProjectValidationResult validation =
                    projectValidator.validate(candidate);
            if (!validation.isPass()) {
                return new PatchDryRunResult(
                        false,
                        "patch dry-run validation failed:"
                                + validation.message(),
                        candidate
                );
            }
            if (!healthGate.isHealthy(candidate)) {
                return new PatchDryRunResult(
                        false,
                        "patch dry-run health gate failed",
                        candidate
                );
            }
            return new PatchDryRunResult(
                    true,
                    "DRY_RUN_PASS",
                    candidate
            );
        } catch (RuntimeException error) {
            return new PatchDryRunResult(
                    false,
                    error.getMessage() == null
                            ? "patch dry-run failed"
                            : error.getMessage(),
                    null
            );
        }
    }

    public synchronized PatchApplyResult apply(
            PatchManifest manifest,
            PatchPayload payload,
            RemoteVerificationProof proof
    ){
        String rejected = validateInputs(manifest, payload, proof);
        if (rejected != null) return rejected(rejected);

        PatchDryRunResult dryRun = dryRun(
                manifest,
                payload,
                proof
        );
        if (!dryRun.isPass()) {
            return rejected(dryRun.reason());
        }

        long baseRevision = projectManager.savedRevision();
        boolean mutationStarted = false;
        journal.begin(manifest, payload);
        try {
            projectManager.captureFinalRecoverySnapshot();
            journal.phase(
                    PatchTransactionJournal.Phase.SNAPSHOT_READY
            );

            journal.phase(
                    PatchTransactionJournal.Phase.MUTATING
            );
            projectManager.applyResourceTransaction(
                    payload.upserts(),
                    payload.deletes()
            );
            mutationStarted = true;
            ProjectState committed = projectManager.save();

            journal.phase(
                    PatchTransactionJournal.Phase.VERIFYING
            );
            if (committed.revision()
                    != manifest.targetRevision()) {
                throw new IOException(
                        "patch target revision mismatch"
                );
            }

            ProjectValidationResult validation =
                    projectValidator.validate(committed);
            if (!validation.isPass()) {
                throw new IOException(
                        "patch validation failed: "
                                + validation.message()
                );
            }

            activationHook.onActivated(committed);

            journal.phase(
                    PatchTransactionJournal.Phase.HEALTH_CHECK
            );
            if (!healthGate.isHealthy(committed)) {
                throw new IOException(
                        "patch post-activation health check failed"
                );
            }

            journal.phase(
                    PatchTransactionJournal.Phase.COMMITTING
            );
            journal.clear();
            recoveryManager.clearRecoveryRequired();

            return new PatchApplyResult(
                    PatchApplyResult.State.APPLIED,
                    "remote verified, dry-run pass, health pass, committed",
                    committed.revision()
            );
        } catch (Exception error) {
            if (!mutationStarted) {
                journal.clear();
                return rejected("recovery point unavailable");
            }
            journal.phase(
                    PatchTransactionJournal.Phase.ROLLING_BACK
            );
            try {
                ProjectState restored =
                        projectManager.restoreRevision(baseRevision);
                activationHook.onActivated(restored);
                ProjectValidationResult restoredValidation =
                        projectValidator.validate(restored);
                if (!restoredValidation.isPass()
                        || !healthGate.isHealthy(restored)) {
                    throw new IOException(
                            "rollback health check failed"
                    );
                }
                journal.clear();
                recoveryManager.clearRecoveryRequired();
                return new PatchApplyResult(
                        PatchApplyResult.State.RESTORED,
                        "patch failed and prior revision restored",
                        restored.revision()
                );
            } catch (Exception restoreError) {
                journal.phase(
                        PatchTransactionJournal.Phase.FAILED_SAFE
                );
                recoveryManager.markRecoveryRequired(
                        "PATCH_ROLLBACK_FAILED",
                        manifest.patchId()
                );
                return new PatchApplyResult(
                        PatchApplyResult.State.FAILED_SAFE,
                        "patch restore failed safely",
                        projectManager.savedRevision()
                );
            }
        }
    }

    public synchronized PatchApplyResult restore(long revision){
        journal.phase(PatchTransactionJournal.Phase.ROLLING_BACK);
        try{
            ProjectState restored =
                    projectManager.restoreRevision(revision);
            activationHook.onActivated(restored);
            ProjectValidationResult validation =
                    projectValidator.validate(restored);
            if (!validation.isPass()
                    || !healthGate.isHealthy(restored)) {
                throw new IOException(
                        "explicit restore health check failed"
                );
            }
            journal.clear();
            recoveryManager.clearRecoveryRequired();
            return new PatchApplyResult(
                    PatchApplyResult.State.RESTORED,
                    "explicit safe restore complete",
                    restored.revision()
            );
        }catch(Exception error){
            journal.phase(
                    PatchTransactionJournal.Phase.FAILED_SAFE
            );
            recoveryManager.markRecoveryRequired(
                    "PATCH_EXPLICIT_RESTORE_FAILED",
                    "RESTORE"
            );
            return new PatchApplyResult(
                    PatchApplyResult.State.FAILED_SAFE,
                    "explicit safe restore failed",
                    projectManager.savedRevision()
            );
        }
    }

    private String validateInputs(
            PatchManifest manifest,
            PatchPayload payload,
            RemoteVerificationProof proof
    ) {
        if (manifest == null || payload == null || proof == null) {
            return "patch input missing";
        }

        ProjectState current = projectManager.current();
        if (!current.projectId().equals(manifest.projectId())) {
            return "patch project identity mismatch";
        }
        if (projectManager.hasUnsavedChanges()
                || projectManager.savedRevision() <= 0
                || projectManager.savedRevision()
                    != manifest.baseRevision()
                || current.revision()
                    != manifest.baseRevision()) {
            return "patch base revision is not clean/current";
        }
        if (recoveryManager.isRecoveryRequired()) {
            return "recovery required before patch";
        }
        if (!manifest.payloadSha256().equals(payload.sha256())) {
            return "patch payload digest mismatch";
        }
        if (manifest.schemaVersion() >= 2
                && !runtimeApkIdentityBound()) {
            return "patch runtime apk identity unbound";
        }
        if (runtimeApkIdentityBound()) {
            if (!runtimeParentApkSha256.equals(
                    manifest.parentSignedApkSha256()
            )) {
                return "patch parent signed APK mismatch";
            }
            if (!runtimeRollbackBaselineApkSha256.equals(
                    manifest.rollbackBaselineApkSha256()
            )) {
                return "patch rollback baseline APK mismatch";
            }
        }
        if (!manifest.supportsHost(
                hostPackageName,
                hostVersionCode,
                hostCapabilities
        )) {
            return "patch host compatibility mismatch";
        }
        if (!projectManager.current()
                .dependencyRefs()
                .containsAll(manifest.dependencies())) {
            return "patch dependency requirement missing";
        }
        if (!remoteVerifier.verify(manifest, payload, proof)) {
            return "remote verification failed";
        }
        return null;
    }

    private static void requireSha256(
            String value,
            String label
    ) {
        if (value == null
                || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    label + " invalid"
            );
        }
    }

    private PatchApplyResult rejected(String message){
        return new PatchApplyResult(
                PatchApplyResult.State.REJECTED,
                message,
                projectManager.savedRevision()
        );
    }
}
