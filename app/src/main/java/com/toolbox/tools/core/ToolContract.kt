package com.toolbox.tools.core

interface ToolContract {
    val id: String
    val version: Int
    fun validate(): Boolean
}
