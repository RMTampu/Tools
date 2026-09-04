package com.toolbox.tools.core

class EngineManager {
    private val engines = mutableMapOf<String, EngineContract>()
    private val states = mutableMapOf<String, EngineState>()

    fun addEngine(engine: EngineContract): Boolean {
        if (engine.id.isBlank()) return false
        engines[engine.id] = engine
        states[engine.id] = EngineState.CREATED
        return true
    }

    fun start(id: String): Boolean {
        val engine = engines[id] ?: return false
        engine.start()
        states[id] = EngineState.RUNNING
        return true
    }

    fun stop(id: String): Boolean {
        val engine = engines[id] ?: return false
        engine.stop()
        states[id] = EngineState.STOPPED
        return true
    }

    fun state(id: String): EngineState? = states[id]

    fun hasEngine(id: String): Boolean = engines.containsKey(id)
}
