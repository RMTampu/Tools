package com.toolbox.tools.core

object CoreValidator {
    fun validateTool(tool: ToolContract): Boolean {
        return tool.id.isNotBlank() && tool.version > 0 && tool.validate()
    }
}
