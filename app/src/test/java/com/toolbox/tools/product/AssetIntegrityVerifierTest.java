package com.toolbox.tools.product;

import com.toolbox.tools.core.MemoryVisibleWorkspaceStore;
import com.toolbox.tools.core.VisibleWorkspaceStore;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public final class AssetIntegrityVerifierTest {
    @Test
    public void exactBytesPassAndCorruptionFails() throws Exception {
        MemoryVisibleWorkspaceStore visible =
                new MemoryVisibleWorkspaceStore();
        visible.ensureLayout();

        byte[] bytes = "asset-original".getBytes(
                StandardCharsets.UTF_8
        );
        AssetIntegrityVerifier verifier =
                new AssetIntegrityVerifier();
        String sha = verifier.sha256(bytes);

        visible.write(
                VisibleWorkspaceStore.Area.ASSETS,
                "asset.bin",
                bytes
        );
        assertTrue(
                verifier.verify(
                        visible,
                        VisibleWorkspaceStore.Area.ASSETS,
                        "asset.bin",
                        sha
                )
        );

        visible.write(
                VisibleWorkspaceStore.Area.ASSETS,
                "asset.bin",
                "asset-corrupt".getBytes(
                        StandardCharsets.UTF_8
                )
        );
        assertFalse(
                verifier.verify(
                        visible,
                        VisibleWorkspaceStore.Area.ASSETS,
                        "asset.bin",
                        sha
                )
        );
    }

    @Test(expected = java.io.IOException.class)
    public void budgetIsEnforcedBeforeMaterialization()
            throws Exception {
        MemoryVisibleWorkspaceStore visible =
                new MemoryVisibleWorkspaceStore();
        visible.ensureLayout();

        byte[] bytes = new byte[2048];
        AssetIntegrityVerifier verifier =
                new AssetIntegrityVerifier();
        visible.write(
                VisibleWorkspaceStore.Area.ASSETS,
                "large.bin",
                bytes
        );
        verifier.verify(
                visible,
                VisibleWorkspaceStore.Area.ASSETS,
                "large.bin",
                verifier.sha256(bytes),
                1024
        );
    }
}
