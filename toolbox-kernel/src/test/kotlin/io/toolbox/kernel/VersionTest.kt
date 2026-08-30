package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun `semver official prerelease precedence chain is preserved`() {
        val ordered = listOf(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0"
        ).map(ModuleVersion::parse)

        ordered.zipWithNext().forEach { (left, right) ->
            assertTrue(left < right, "$left must be lower precedence than $right")
        }
    }

    @Test
    fun `build metadata does not affect precedence`() {
        val first = ModuleVersion.parse("1.2.3-alpha.1+build.7")
        val second = ModuleVersion.parse("1.2.3-alpha.1+build.99")
        assertEquals(0, first.compareTo(second))
        assertTrue(VersionRange.exact(first).contains(second))
    }

    @Test
    fun `numeric prerelease identifiers compare numerically without overflow`() {
        assertTrue(ModuleVersion.parse("1.0.0-beta.2") < ModuleVersion.parse("1.0.0-beta.11"))
        assertTrue(
            ModuleVersion.parse("1.0.0-999999999999999999999999999") <
                ModuleVersion.parse("1.0.0-1000000000000000000000000000")
        )
    }

    @Test
    fun `numeric prerelease identifiers reject leading zeroes`() {
        assertFailsWith<IllegalArgumentException> { ModuleVersion.parse("1.0.0-alpha.01") }
    }

    @Test
    fun `semantic version requires major minor and patch`() {
        assertFailsWith<IllegalArgumentException> { ModuleVersion.parse("1") }
        assertFailsWith<IllegalArgumentException> { ModuleVersion.parse("1.0") }
    }

    @Test
    fun `build metadata is preserved in rendering`() {
        val version = ModuleVersion.parse("2.4.6-rc.2+sha.abc123")
        assertEquals("2.4.6-rc.2+sha.abc123", version.toString())
    }

    @Test
    fun `exclusive zero width range is rejected even when versions differ only by metadata`() {
        val first = ModuleVersion.parse("1.0.0+one")
        val second = ModuleVersion.parse("1.0.0+two")
        assertEquals(0, first.compareTo(second))
        assertFailsWith<IllegalArgumentException> {
            VersionRange(first, second, includeMinimum = true, includeMaximum = false)
        }
    }

    @Test
    fun `prerelease is excluded by release-only lower bound when appropriate`() {
        val range = VersionRange.atLeast(ModuleVersion.parse("1.0.0"))
        assertFalse(range.contains(ModuleVersion.parse("1.0.0-rc.1")))
        assertTrue(range.contains(ModuleVersion.parse("1.0.0")))
    }
}
