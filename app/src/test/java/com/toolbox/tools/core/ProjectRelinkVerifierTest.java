package com.toolbox.tools.core;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class ProjectRelinkVerifierTest {
    @Test
    public void relinkRequiresSameProjectIdentityAndValidManifest() {
        ProjectRelinkVerifier verifier = new ProjectRelinkVerifier();
        ProjectState candidate = ProjectState.create("project.alpha");

        assertEquals(
                ProjectAccessStatus.PROJECT_OK,
                verifier.verify("project.alpha", candidate, true)
        );
        assertEquals(
                ProjectAccessStatus.PROJECT_CORRUPT,
                verifier.verify("project.other", candidate, true)
        );
        assertEquals(
                ProjectAccessStatus.PROJECT_CORRUPT,
                verifier.verify("project.alpha", candidate, false)
        );
    }

    @Test
    public void relinkRejectsUnsupportedSchema() {
        ProjectRelinkVerifier verifier = new ProjectRelinkVerifier();
        ProjectState candidate = ProjectState.restore(
                "project.alpha",
                0,
                ProjectState.CURRENT_BUILD_MODEL_VERSION,
                0,
                ProjectLifecycle.ACTIVE,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet()
        );

        assertEquals(
                ProjectAccessStatus.SCHEMA_INCOMPATIBLE,
                verifier.verify("project.alpha", candidate, true)
        );
    }
}
