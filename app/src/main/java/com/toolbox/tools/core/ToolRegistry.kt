package com.toolbox.tools.core

class ToolRegistry {
    private val tools = mutableMapOf<String, Any>()

    fun register(id: String, tool: Any) {
        tools[id] = tool
    }

    fun get(id: String): Any? = tools[id]

    fun ids(): Set<String> = tools.keys
}
