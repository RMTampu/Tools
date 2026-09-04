package com.toolbox.tools.core

data class ToolDescriptor(
    val id: String,
    val version: Int,
    val enabled: Boolean = true
)
