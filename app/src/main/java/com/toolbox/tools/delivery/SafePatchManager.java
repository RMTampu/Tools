package com.toolbox.tools.delivery;

import com.toolbox.tools.core.ProjectManager;
import com.toolbox.tools.core.ProjectState;
import com.toolbox.tools.core.ProjectValidationResult;
import com.toolbox.tools.core.ProjectValidator;
import com.toolbox.tools.core.RecoveryManager;
import java.io.IOException;
import java.util.Objects;

public final class SafePatchManager {
    private final ProjectManager projectManager;
    private final RecoveryManager recoveryManager;
    private final RemotePatchVerifier remoteVerifier;
    private final ProjectValidator projectValidator=new ProjectValidator();

    public SafePatchManager(
            ProjectManager projectManager,
            RecoveryManager recoveryManager,
            RemotePatchVerifier remoteVerifier
    ){
        this.projectManager=Objects.requireNonNull(projectManager,"projectManager");
        this.recoveryManager=Objects.requireNonNull(recoveryManager,"recoveryManager");
        this.remoteVerifier=Objects.requireNonNull(remoteVerifier,"remoteVerifier");
    }

    public synchronized PatchApplyResult apply(
            PatchManifest manifest,
            PatchPayload payload,
            RemoteVerificationProof proof
    ){
        if(manifest==null||payload==null||proof==null) {
            return rejected("patch input missing");
        }

        ProjectState current=projectManager.current();
        if(!current.projectId().equals(manifest.projectId())) {
            return rejected("patch project identity mismatch");
        }
        if(projectManager.hasUnsavedChanges()
                ||projectManager.savedRevision()<=0
                ||projectManager.savedRevision()!=manifest.baseRevision()
                ||current.revision()!=manifest.baseRevision()) {
            return rejected("patch base revision is not clean/current");
        }
        if(recoveryManager.isRecoveryRequired()) {
            return rejected("recovery required before patch");
        }
        if (!remoteVerifier.verify(manifest, payload, proof)) {
            return rejected("remote verification failed");
        }

        long baseRevision=projectManager.savedRevision();
        boolean mutationStarted=false;
        try{
            projectManager.captureFinalRecoverySnapshot();
            projectManager.applyResourceTransaction(
                    payload.upserts(),
                    payload.deletes()
            );
            mutationStarted=true;
            ProjectState committed=projectManager.save();

            if(committed.revision()!=manifest.targetRevision()) {
                throw new IOException("patch target revision mismatch");
            }
            ProjectValidationResult validation=projectValidator.validate(committed);
            if(!validation.isPass()) {
                throw new IOException(
                        "patch validation failed: "+validation.message()
                );
            }

            return new PatchApplyResult(
                    PatchApplyResult.State.APPLIED,
                    "remote verified and applied",
                    committed.revision()
            );
        }catch(Exception error){
            if(!mutationStarted) {
                return rejected("recovery point unavailable");
            }
            try{
                ProjectState restored=projectManager.restoreRevision(baseRevision);
                recoveryManager.clearRecoveryRequired();
                return new PatchApplyResult(
                        PatchApplyResult.State.RESTORED,
                        "patch failed and prior revision restored",
                        restored.revision()
                );
            }catch(Exception restoreError){
                recoveryManager.markRecoveryRequired();
                return new PatchApplyResult(
                        PatchApplyResult.State.FAILED_SAFE,
                        "patch restore failed safely",
                        projectManager.savedRevision()
                );
            }
        }
    }

    public synchronized PatchApplyResult restore(long revision){
        try{
            ProjectState restored=projectManager.restoreRevision(revision);
            recoveryManager.clearRecoveryRequired();
            return new PatchApplyResult(
                    PatchApplyResult.State.RESTORED,
                    "explicit safe restore complete",
                    restored.revision()
            );
        }catch(Exception error){
            recoveryManager.markRecoveryRequired();
            return new PatchApplyResult(
                    PatchApplyResult.State.FAILED_SAFE,
                    "explicit safe restore failed",
                    projectManager.savedRevision()
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
