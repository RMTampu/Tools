package com.toolbox.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreFoundationTest {

    private class SampleTool : ToolContract {
        override val id = "sample"
        override val version = 1
        override fun validate(): Boolean = true
    }

    private class SampleEngine : EngineContract {
        override val id = "engine"
        override fun start() {}
        override fun stop() {}
    }

    @Test
    fun registry_accepts_valid_tool() {
        val registry = ToolRegistry()
        assertTrue(registry.register(SampleTool()))
    }

    @Test
    fun engine_tracks_lifecycle_state() {
        val manager = EngineManager()
        manager.addEngine(SampleEngine())
        manager.start("engine")
        assertEquals(EngineState.RUNNING, manager.state("engine"))
    }

    @Test
    fun config_store_has_versioned_state() {
        val store = ConfigStore(2)
        store.put("mode", "normal")
        assertEquals(2, store.version())
        assertTrue(store.contains("mode"))
    }
}
