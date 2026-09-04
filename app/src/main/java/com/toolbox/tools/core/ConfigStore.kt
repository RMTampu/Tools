package com.toolbox.tools.core

class ConfigStore {
    private val values = mutableMapOf<String, String>()

    fun put(key: String, value: String) {
        values[key] = value
    }

    fun get(key: String): String? = values[key]
}
