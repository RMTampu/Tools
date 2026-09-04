package com.toolbox.tools.delivery;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.RecoveryCandidate;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Collections;
import static org.junit.Assert.*;

public class SafePatchManagerTest {
    @Test
    public void applyCapturesRecoveryPointBeforeMutation() throws Exception {
        Fixture f=fixture();
        long base=f.kernel.projectManager().savedRevision();
        PatchPayload payload=new PatchPayload(
                Collections.singletonMap("screen.patch.safe","stage11"),
                Collections.emptySet()
        );
        PatchManifest manifest=manifest(payload,base);
        PatchApplyResult result=f.manager.apply(
                manifest,
                payload,
                sign(f.pair,f.identity,manifest)
        );

        assertEquals(PatchApplyResult.State.APPLIED,result.state());
        assertEquals(
                "stage11",
                f.kernel.projectManager().current().resources()
                        .get("screen.patch.safe")
        );

        boolean found=false;
        for(RecoveryCandidate candidate
                :f.kernel.projectManager().recoveryCandidates()) {
            if(candidate.kind()
                    ==RecoveryCandidate.Kind.FINAL_RECOVERY_SNAPSHOT
                    &&candidate.revision()==base) {
                found=true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void invalidRemoteProofIsRejectedWithoutMutation() throws Exception {
        Fixture f=fixture();
        long base=f.kernel.projectManager().savedRevision();
        PatchPayload payload=new PatchPayload(
                Collections.singletonMap("screen.patch.reject","unsafe"),
                Collections.emptySet()
        );
        PatchManifest manifest=manifest(payload,base);
        KeyPair attacker=KeyPairGenerator.getInstance("RSA").generateKeyPair();

        PatchApplyResult result=f.manager.apply(
                manifest,
                payload,
                sign(attacker,f.identity,manifest)
        );

        assertEquals(PatchApplyResult.State.REJECTED,result.state());
        assertEquals(base,f.kernel.projectManager().savedRevision());
        assertFalse(
                f.kernel.projectManager().current().resources()
                        .containsKey("screen.patch.reject")
        );
        assertFalse(f.kernel.projectManager().hasUnsavedChanges());
    }

    @Test
    public void explicitSafeRestoreRevertsAppliedPatch() throws Exception {
        Fixture f=fixture();
        long base=f.kernel.projectManager().savedRevision();
        PatchPayload payload=new PatchPayload(
                Collections.singletonMap("screen.patch.restore","stage11"),
                Collections.emptySet()
        );
        PatchManifest manifest=manifest(payload,base);

        assertEquals(
                PatchApplyResult.State.APPLIED,
                f.manager.apply(
                        manifest,
                        payload,
                        sign(f.pair,f.identity,manifest)
                ).state()
        );

        assertEquals(
                PatchApplyResult.State.RESTORED,
                f.manager.restore(base).state()
        );
        assertFalse(
                f.kernel.projectManager().current().resources()
                        .containsKey("screen.patch.restore")
        );
    }

    @Test(expected=IllegalArgumentException.class)
    public void protectedResourceCannotEnterPatch(){
        new PatchPayload(
                Collections.singletonMap("recovery.override","bad"),
                Collections.emptySet()
        );
    }

    private static Fixture fixture()throws Exception{
        AppKernel kernel=AppKernel.createDefault();
        kernel.projectManager().save();
        KeyPair pair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String identity=sha256(pair.getPublic().getEncoded());
        return new Fixture(
                kernel,
                pair,
                identity,
                new SafePatchManager(
                        kernel.projectManager(),
                        kernel.recoveryManager(),
                        new RemotePatchVerifier(pair.getPublic(),identity)
                )
        );
    }

    private static PatchManifest manifest(PatchPayload payload,long base){
        return new PatchManifest(
                "patch.safe."+base,
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

    private static final class Fixture{
        final AppKernel kernel;
        final KeyPair pair;
        final String identity;
        final SafePatchManager manager;

        Fixture(
                AppKernel kernel,
                KeyPair pair,
                String identity,
                SafePatchManager manager
        ){
            this.kernel=kernel;
            this.pair=pair;
            this.identity=identity;
            this.manager=manager;
        }
    }
}
