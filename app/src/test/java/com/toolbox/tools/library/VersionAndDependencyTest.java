package com.toolbox.tools.library;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class VersionAndDependencyTest {
    @Test
    public void semanticVersionOrderingAndRangeAreDeterministic() {
        VersionNumber v100 = VersionNumber.parse("1.0.0");
        VersionNumber v150 = VersionNumber.parse("1.5");
        VersionNumber v200 = VersionNumber.parse("2");

        VersionRange range = VersionRange.majorCompatible(v100);

        assertTrue(range.contains(v100));
        assertTrue(range.contains(v150));
        assertFalse(range.contains(v200));
        assertTrue(v150.compareTo(v100) > 0);
    }

    @Test
    public void malformedVersionsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> VersionNumber.parse("01.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionNumber.parse("1.-1.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionNumber.parse("1.2.3.4"));
    }
}
