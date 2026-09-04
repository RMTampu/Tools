package com.toolbox.tools.core

class EngineManager {
    private val engines = mutableMapOf<String, Any>()

    fun addEngine(id: String, engine: Any) {
        engines[id] = engine
    }

    fun hasEngine(id: String): Boolean = engines.containsKey(id)
}
