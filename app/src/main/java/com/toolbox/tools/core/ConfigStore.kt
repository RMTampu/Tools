package com.toolbox.tools.core

class ConfigStore(private val schemaVersion: Int = 1) {
    private val values = mutableMapOf<String, String>()

    fun version(): Int = schemaVersion

    fun put(key: String, value: String) {
        require(key.isNotBlank())
        values[key] = value
    }

    fun get(key: String): String? = values[key]

    fun contains(key: String): Boolean = values.containsKey(key)

    fun clear() {
        values.clear()
    }
}
