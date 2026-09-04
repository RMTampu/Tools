package com.toolbox.tools.delivery;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Collections;
import static org.junit.Assert.*;

public class RemotePatchVerifierTest {
    @Test
    public void validRemoteSignaturePassesAndTamperingFails() throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String identity=sha256(pair.getPublic().getEncoded());
        RemotePatchVerifier verifier=
                new RemotePatchVerifier(pair.getPublic(),identity);
        PatchPayload payload=new PatchPayload(
                Collections.singletonMap("screen.patch.demo","value"),
                Collections.emptySet()
        );
        PatchManifest manifest=manifest(payload,1);
        RemoteVerificationProof proof=sign(pair,identity,manifest);

        assertTrue(verifier.verify(manifest,payload,proof));

        PatchPayload tampered=new PatchPayload(
                Collections.singletonMap("screen.patch.demo","tampered"),
                Collections.emptySet()
        );
        assertFalse(verifier.verify(manifest,tampered,proof));

        RemoteVerificationProof wrongIdentity=new RemoteVerificationProof(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                proof.algorithm(),
                proof.signatureBase64()
        );
        assertFalse(verifier.verify(manifest,payload,wrongIdentity));
    }

    private static PatchManifest manifest(PatchPayload payload,long base){
        return new PatchManifest(
                "patch.test."+base,
                "project.default",
                base,
                base+1,
                "fbc39153bc121ed2d32bc9c24e9ff8f0e9b7730fcef01021f4adfd830fbd21ff",
                "1111111111111111111111111111111111111111111111111111111111111111",
                "741ebcf799280fbba1b4c7d2e60ba157ba133e3f6545b3468882373150f024f7",
                payload.sha256()
        );
    }

    private static RemoteVerificationProof sign(
            KeyPair pair,
            String identity,
            PatchManifest manifest
    )throws Exception{
        Signature signature=Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(
                RemotePatchVerifier.signedMessage(manifest)
                        .getBytes(StandardCharsets.UTF_8)
        );
        return new RemoteVerificationProof(
                identity,
                "SHA256withRSA",
                Base64.getEncoder().encodeToString(signature.sign())
        );
    }

    private static String sha256(byte[] value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out=new StringBuilder();
        for(byte item:digest) {
            out.append(String.format(java.util.Locale.ROOT,"%02x",item));
        }
        return out.toString();
    }
}
