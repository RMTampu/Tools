package com.toolbox.tools.build;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.core.VisibleWorkspaceStore;

import org.junit.Test;

import static org.junit.Assert.*;

public final class BuildHandoffManagerTest {
    private static final String SIGNER =
            "290fb37d527935766e327781833493400dd647cfc8bdbe433254a2df52e4b8e4";
    private static final String BASELINE =
            "4f4579d87d867524e1b308de1a9a39ac2be0a18894d9317eea60a67dc4d91c05";

    @Test
    public void buildHandoffIsPhysicalDeterministicAndSourceBound()
            throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        BuildProvenance provenance = provenance(
                "1111111111111111111111111111111111111111"
        );

        BuildHandoffPackage first =
                kernel.buildHandoffManager().prepare(provenance);
        BuildHandoffPackage second =
                kernel.buildHandoffManager().prepare(provenance);

        assertEquals(first.buildId(), second.buildId());
        assertEquals(
                first.packageContentSha256(),
                second.packageContentSha256()
        );
        assertTrue(first.fileCount() >= 3);
        assertTrue(
                kernel.visibleWorkspaceStore().exists(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        first.manifestFile()
                )
        );
        assertTrue(
                kernel.visibleWorkspaceStore().exists(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        first.irFile()
                )
        );
        assertTrue(
                kernel.visibleWorkspaceStore().exists(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        first.projectFile()
                )
        );

        String manifest = new String(
                kernel.visibleWorkspaceStore().read(
                        VisibleWorkspaceStore.Area.EXPORTS,
                        first.manifestFile()
                ),
                java.nio.charset.StandardCharsets.UTF_8
        );
        assertTrue(manifest.contains(
                "STATUS=IMMUTABLE_READY_TO_BUILD"
        ));
        assertTrue(manifest.contains(
                "SOURCE_COMMIT_SHA="
                        + provenance.sourceCommitSha()
        ));
        assertTrue(manifest.contains(
                "EXPECTED_SIGNER_SHA256=" + SIGNER
        ));
        assertTrue(manifest.contains("SECRET_INCLUDED=NO"));
        assertTrue(manifest.contains("CACHE_INCLUDED=NO"));
        assertTrue(manifest.contains("RECOVERY_INCLUDED=NO"));
    }

    @Test
    public void sourceCommitChangesBuildIdentity() throws Exception {
        AppKernel firstKernel = AppKernel.createDefault();
        AppKernel secondKernel = AppKernel.createDefault();

        BuildHandoffPackage first =
                firstKernel.buildHandoffManager().prepare(
                        provenance(
                                "1111111111111111111111111111111111111111"
                        )
                );
        BuildHandoffPackage second =
                secondKernel.buildHandoffManager().prepare(
                        provenance(
                                "2222222222222222222222222222222222222222"
                        )
                );

        assertNotEquals(first.buildId(), second.buildId());
    }

    @Test
    public void unboundSourceIsRejected() {
        AppKernel kernel = AppKernel.createDefault();
        BuildProvenance unbound = provenance(
                "0000000000000000000000000000000000000000"
        );

        Exception error = assertThrows(
                Exception.class,
                () -> kernel.buildHandoffManager().prepare(unbound)
        );
        assertTrue(
                error.getMessage().contains(
                        "BUILD_SOURCE_PROVENANCE_UNBOUND"
                )
        );
    }

    private static BuildProvenance provenance(String sha) {
        return new BuildProvenance(
                "RMTampu/Tools",
                sha,
                "baseline-v12-maximal-work",
                "RMTampu/Tools",
                "RMTampu/Tools/.github/workflows/"
                        + "product-full-branch-ci.yml@refs/heads/"
                        + "baseline-v12-maximal-work",
                "gradle=8.2.1;agp=8.2.2;jdk=17;"
                        + "compileSdk=30;targetSdk=30",
                SIGNER,
                BASELINE
        );
    }
}
