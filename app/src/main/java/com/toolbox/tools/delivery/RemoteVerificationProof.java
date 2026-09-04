package com.toolbox.tools.delivery;

import java.util.Base64;

public final class RemoteVerificationProof {
    private final String signerIdentitySha256;
    private final String algorithm;
    private final String signatureBase64;

    public RemoteVerificationProof(
            String signerIdentitySha256,
            String algorithm,
            String signatureBase64
    ){
        if(signerIdentitySha256==null
                ||!signerIdentitySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("signer identity invalid");
        }
        if(!"SHA256withRSA".equals(algorithm)
                &&!"SHA256withECDSA".equals(algorithm)) {
            throw new IllegalArgumentException("signature algorithm invalid");
        }
        if(signatureBase64==null||signatureBase64.length()>16384) {
            throw new IllegalArgumentException("signature invalid");
        }
        try{
            if(Base64.getDecoder().decode(signatureBase64).length==0) {
                throw new IllegalArgumentException("signature empty");
            }
        } catch(IllegalArgumentException error){
            throw new IllegalArgumentException("signature base64 invalid",error);
        }
        this.signerIdentitySha256=signerIdentitySha256;
        this.algorithm=algorithm;
        this.signatureBase64=signatureBase64;
    }

    public String signerIdentitySha256(){return signerIdentitySha256;}
    public String algorithm(){return algorithm;}
    public String signatureBase64(){return signatureBase64;}
}
