package io.toolbox.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeCompatibilityTest {
    @Test
    fun acceptsExactlyAndroid11Arm64() {
        assertTrue(RuntimeCompatibility.evaluate(30, listOf("arm64-v8a")).supported)
    }

    @Test
    fun rejectsWrongApiWithoutFallback() {
        assertFalse(RuntimeCompatibility.evaluate(31, listOf("arm64-v8a")).supported)
        assertFalse(RuntimeCompatibility.evaluate(29, listOf("arm64-v8a")).supported)
    }

    @Test
    fun rejectsMissingArm64WithoutFallback() {
        assertFalse(RuntimeCompatibility.evaluate(30, listOf("x86_64")).supported)
        assertFalse(RuntimeCompatibility.evaluate(30, listOf("armeabi-v7a")).supported)
    }
}
