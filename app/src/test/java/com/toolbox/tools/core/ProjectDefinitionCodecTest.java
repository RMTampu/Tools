package com.toolbox.tools.core;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ProjectDefinitionCodecTest {
    @Test
    public void definitionRoundTripKeepsResourcesExternalAndDeterministic() {
        ProjectState state = ProjectState.create("project.alpha")
                .withResource("screen.main", "{screen}")
                .withResource("asset.logo", "{asset}")
                .withReference("screen.main", "asset.logo")
                .withDependency("component.button")
                .withRevision(7);

        ProjectDefinitionCodec codec = new ProjectDefinitionCodec();
        String first = codec.encode(state);
        String second = codec.encode(state);

        assertEquals(first, second);
        assertFalse(first.contains("{screen}"));
        assertFalse(first.contains("{asset}"));
        assertEquals(state, codec.decode(first, state.resources()));
    }

    @Test
    public void checksumMutationIsRejected() {
        ProjectState state = ProjectState.create("project.alpha")
                .withResource("screen.main", "one");
        ProjectDefinitionCodec codec = new ProjectDefinitionCodec();
        String encoded = codec.encode(state);

        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(
                        encoded.replace("revision=0", "revision=9"),
                        state.resources()
                )
        );
    }

    @Test
    public void missingReferenceFailsClosed() {
        ProjectState state = ProjectState.create("project.alpha")
                .withResource("screen.main", "one")
                .withReference("screen.main", "asset.missing");

        ProjectValidationResult result = new ProjectValidator().validate(state);

        assertFalse(result.isPass());
        assertTrue(result.message().contains("REFERENCE_TARGET_MISSING"));
    }

    @Test
    public void invalidStableIdIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectState.create("../escape")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectState.restore(
                        "project.alpha",
                        1,
                        1,
                        0,
                        ProjectLifecycle.ACTIVE,
                        Collections.singletonMap("../bad", "x"),
                        Collections.emptyMap(),
                        Collections.emptySet()
                )
        );
    }
}
