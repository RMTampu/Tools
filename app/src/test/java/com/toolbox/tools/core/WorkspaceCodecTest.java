package com.toolbox.tools.core;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class WorkspaceCodecTest {
    @Test
    public void roundTripIsDeterministicAndLossless() {
        WorkspaceSnapshot snapshot = WorkspaceSnapshot.create("toolbox.test")
                .withValue("title", "Alpha", 1)
                .withValue("mode", "visual", 2);

        WorkspaceCodec codec = new WorkspaceCodec();
        String first = codec.encode(snapshot);
        String second = codec.encode(snapshot);

        assertEquals(first, second);
        assertEquals(snapshot, codec.decode(first));
    }

    @Test
    public void corruptionIsRejected() {
        WorkspaceSnapshot snapshot = WorkspaceSnapshot.create("toolbox.test")
                .withValue("title", "Alpha", 1);
        WorkspaceCodec codec = new WorkspaceCodec();
        String encoded = codec.encode(snapshot);
        String corrupted = encoded.replace("revision=1", "revision=9");

        assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupted));
    }

    @Test
    public void unsupportedSchemaIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkspaceSnapshot.restore(
                        "toolbox.test",
                        99,
                        0,
                        Collections.emptyMap()
                )
        );
    }
}
