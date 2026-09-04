package com.toolbox.tools.live;

import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CapabilityScannerTest {
    @Test
    public void selfTargetClassifiesAllCapabilityAreas() {
        TargetDescriptor target = DefaultLiveFactory.selfTarget();

        CapabilityScanResult result =
                new CapabilityScanner().scan(target);

        assertTrue(result.installed());
        assertTrue(result.liveAvailable());
        assertEquals(
                CapabilityAvailability.AVAILABLE,
                result.status(CapabilityArea.UI)
        );
        assertEquals(
                CapabilityAvailability.READ_ONLY,
                result.status(CapabilityArea.LOGIC)
        );
        assertEquals(
                CapabilityAvailability.AVAILABLE,
                result.status(CapabilityArea.DATA)
        );
        assertEquals(
                CapabilityAvailability.AVAILABLE,
                result.status(CapabilityArea.BINDING)
        );
        assertEquals(
                CapabilityAvailability.AVAILABLE,
                result.status(CapabilityArea.ASSET)
        );
        assertEquals(
                CapabilityAvailability.AVAILABLE,
                result.status(CapabilityArea.RUNTIME)
        );
    }

    @Test
    public void missingTargetFailsClosed() {
        EnumMap<CapabilityArea, CapabilityAvailability> declared =
                allAvailable();

        TargetDescriptor target = new TargetDescriptor(
                "target.missing",
                "Target Tidak Terpasang",
                false,
                false,
                EditDoor.MANAGED_RUNTIME,
                declared
        );

        CapabilityScanResult result =
                new CapabilityScanner().scan(target);

        assertFalse(result.installed());
        assertFalse(result.liveAvailable());
        for (CapabilityArea area : CapabilityArea.values()) {
            assertEquals(
                    CapabilityAvailability.UNAVAILABLE,
                    result.status(area)
            );
        }
    }

    @Test
    public void noEditDoorCannotClaimWritableCapability() {
        TargetDescriptor target = new TargetDescriptor(
                "target.readonly",
                "Target Baca Saja",
                true,
                false,
                EditDoor.NONE,
                allAvailable()
        );

        CapabilityScanResult result =
                new CapabilityScanner().scan(target);

        assertEquals(
                CapabilityAvailability.READ_ONLY,
                result.status(CapabilityArea.UI)
        );
        assertEquals(
                CapabilityAvailability.READ_ONLY,
                result.status(CapabilityArea.DATA)
        );
        assertEquals(
                CapabilityAvailability.UNAVAILABLE,
                result.status(CapabilityArea.RUNTIME)
        );
        assertFalse(result.liveAvailable());
    }

    private static EnumMap<CapabilityArea, CapabilityAvailability>
    allAvailable() {
        EnumMap<CapabilityArea, CapabilityAvailability> values =
                new EnumMap<>(CapabilityArea.class);
        for (CapabilityArea area : CapabilityArea.values()) {
            values.put(area, CapabilityAvailability.AVAILABLE);
        }
        return values;
    }
}
