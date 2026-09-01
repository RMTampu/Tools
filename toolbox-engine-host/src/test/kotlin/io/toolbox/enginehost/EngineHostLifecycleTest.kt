package io.toolbox.enginehost

import io.toolbox.kernel.ToolBoxKernel
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EngineHostLifecycleTest {
    @Test
    fun `host service exists only while module is started and returns after restart`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()

        assertTrue(kernel.install(host).isEmpty())
        assertNull(kernel.services.get(EngineHost::class.java))

        assertTrue(kernel.start().isEmpty())
        assertSame(host, kernel.services.get(EngineHost::class.java))

        assertTrue(kernel.stop().isEmpty())
        assertNull(kernel.services.get(EngineHost::class.java))

        assertTrue(kernel.start().isEmpty())
        assertSame(host, kernel.services.get(EngineHost::class.java))
    }
}
