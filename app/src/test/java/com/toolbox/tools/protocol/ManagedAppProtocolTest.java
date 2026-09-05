package com.toolbox.tools.protocol;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.*;

public final class ManagedAppProtocolTest {
    @Test
    public void negotiatesOnlyDeclaredCapabilities() {
        ManagedAppProtocol protocol = new ManagedAppProtocol();
        ManagedAppProtocol.Descriptor target =
                new ManagedAppProtocol.Descriptor(
                        "com.example.toolboxaware",
                        ManagedAppProtocol.CURRENT_VERSION,
                        EnumSet.of(
                                ManagedAppProtocol.Capability.UI,
                                ManagedAppProtocol.Capability.ASSET
                        ),
                        "project.example",
                        7
                );
        ManagedAppProtocol.Session session =
                protocol.negotiate(
                        target,
                        EnumSet.of(
                                ManagedAppProtocol.Capability.UI,
                                ManagedAppProtocol.Capability.LOGIC
                        )
                );
        assertEquals(
                EnumSet.of(ManagedAppProtocol.Capability.UI),
                session.granted()
        );
        assertTrue(
                session.can(
                        ManagedAppProtocol.RequestType.PREVIEW_PATCH
                )
        );
        assertTrue(
                session.sessionId().startsWith("session.")
        );
    }

    @Test
    public void rejectsUnsupportedProtocolAndEmptyNegotiation() {
        try {
            new ManagedAppProtocol.Descriptor(
                    "com.example.target",
                    2,
                    EnumSet.of(ManagedAppProtocol.Capability.UI),
                    "project.example",
                    0
            );
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }

        ManagedAppProtocol protocol = new ManagedAppProtocol();
        ManagedAppProtocol.Descriptor target =
                new ManagedAppProtocol.Descriptor(
                        "com.example.target",
                        1,
                        EnumSet.of(ManagedAppProtocol.Capability.ASSET),
                        "project.example",
                        0
                );
        try {
            protocol.negotiate(
                    target,
                    EnumSet.of(ManagedAppProtocol.Capability.LOGIC)
            );
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void parserIgnoresUnknownCapabilities() {
        ManagedAppProtocol protocol = new ManagedAppProtocol();
        Set<ManagedAppProtocol.Capability> values =
                protocol.parseCapabilities(
                        Arrays.asList(
                                "ui",
                                "asset",
                                "unknown"
                        )
                );
        assertTrue(values.contains(
                ManagedAppProtocol.Capability.UI
        ));
        assertTrue(values.contains(
                ManagedAppProtocol.Capability.ASSET
        ));
        assertEquals(2, values.size());
    }
}
