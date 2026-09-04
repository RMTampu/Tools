package com.toolbox.tools.delivery;

import com.toolbox.tools.core.StableId;

public final class PatchManifest {
    public static final int CURRENT_SCHEMA_VERSION=1;
    private final String patchId;
    private final String projectId;
    private final long baseRevision;
    private final long targetRevision;
    private final String parentSignedApkSha256;
    private final String targetCandidateSha256;
    private final String rollbackBaselineApkSha256;
    private final String payloadSha256;
    private final String canonical;
    private final String contentSha256;

    public PatchManifest(
            String patchId,
            String projectId,
            long baseRevision,
            long targetRevision,
            String parentSignedApkSha256,
            String targetCandidateSha256,
            String rollbackBaselineApkSha256,
            String payloadSha256
    ){
        this.patchId=StableId.require(patchId,"patchId");
        this.projectId=StableId.require(projectId,"projectId");
        if(baseRevision<=0||targetRevision!=baseRevision+1) {
            throw new IllegalArgumentException("patch revision chain invalid");
        }
        requireSha256(parentSignedApkSha256,"parentSignedApkSha256");
        requireSha256(targetCandidateSha256,"targetCandidateSha256");
        requireSha256(rollbackBaselineApkSha256,"rollbackBaselineApkSha256");
        requireSha256(payloadSha256,"payloadSha256");
        this.baseRevision=baseRevision;
        this.targetRevision=targetRevision;
        this.parentSignedApkSha256=parentSignedApkSha256;
        this.targetCandidateSha256=targetCandidateSha256;
        this.rollbackBaselineApkSha256=rollbackBaselineApkSha256;
        this.payloadSha256=payloadSha256;
        this.canonical="TBX_PATCH_V1\n"+patchId+"\n"+projectId+"\n"
                +baseRevision+"\n"+targetRevision+"\n"
                +parentSignedApkSha256+"\n"+targetCandidateSha256+"\n"
                +rollbackBaselineApkSha256+"\n"+payloadSha256+"\n";
        this.contentSha256=PatchPayload.sha256(canonical);
    }

    public String patchId(){return patchId;}
    public String projectId(){return projectId;}
    public long baseRevision(){return baseRevision;}
    public long targetRevision(){return targetRevision;}
    public String parentSignedApkSha256(){return parentSignedApkSha256;}
    public String targetCandidateSha256(){return targetCandidateSha256;}
    public String rollbackBaselineApkSha256(){return rollbackBaselineApkSha256;}
    public String payloadSha256(){return payloadSha256;}
    public String canonical(){return canonical;}
    public String contentSha256(){return contentSha256;}

    private static void requireSha256(String value,String label){
        if(value==null||!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label+" invalid");
        }
    }
}
