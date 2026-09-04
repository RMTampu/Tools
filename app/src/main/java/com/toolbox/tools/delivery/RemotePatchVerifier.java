package com.toolbox.tools.delivery;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Objects;

public final class RemotePatchVerifier {
    private final PublicKey trustedKey;
    private final String trustedIdentitySha256;

    public RemotePatchVerifier(PublicKey trustedKey,String trustedIdentitySha256){
        this.trustedKey=Objects.requireNonNull(trustedKey,"trustedKey");
        if(trustedIdentitySha256==null
                ||!trustedIdentitySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("trusted identity invalid");
        }
        this.trustedIdentitySha256=trustedIdentitySha256;
    }

    public boolean verify(
            PatchManifest manifest,
            PatchPayload payload,
            RemoteVerificationProof proof
    ){
        if(manifest==null||payload==null||proof==null) return false;
        if(!manifest.payloadSha256().equals(payload.sha256())) return false;
        if(!trustedIdentitySha256.equals(proof.signerIdentitySha256())) return false;

        String expected;
        if("RSA".equalsIgnoreCase(trustedKey.getAlgorithm())) {
            expected="SHA256withRSA";
        } else if("EC".equalsIgnoreCase(trustedKey.getAlgorithm())) {
            expected="SHA256withECDSA";
        } else {
            return false;
        }
        if(!expected.equals(proof.algorithm())) return false;

        try{
            Signature verifier=Signature.getInstance(expected);
            verifier.initVerify(trustedKey);
            verifier.update(
                    signedMessage(manifest).getBytes(StandardCharsets.UTF_8)
            );
            return verifier.verify(
                    Base64.getDecoder().decode(proof.signatureBase64())
            );
        }catch(Exception error){
            return false;
        }
    }

    public static String signedMessage(PatchManifest manifest){
        return "TBX_REMOTE_PATCH_V1\n"
                +manifest.contentSha256()+"\n"
                +manifest.targetCandidateSha256()+"\n"
                +manifest.rollbackBaselineApkSha256()+"\n";
    }

    public String trustedIdentitySha256(){return trustedIdentitySha256;}
}
