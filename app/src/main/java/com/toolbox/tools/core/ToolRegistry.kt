package com.toolbox.tools.core

class ToolRegistry {
    private val tools = mutableMapOf<String, ToolContract>()

    fun register(tool: ToolContract): Boolean {
        if (!CoreValidator.validateTool(tool)) return false
        tools[tool.id] = tool
        return true
    }

    fun get(id: String): ToolContract? = tools[id]

    fun ids(): Set<String> = tools.keys
}
