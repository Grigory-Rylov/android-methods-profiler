package com.github.grishberg.profiler.common.settings

interface SettingsRepository {
    fun getBoolValueOrDefault(name: String, default: Boolean = false): Boolean
    fun getIntValueOrDefault(name: String, default: Int = 0): Int
    fun getStringValueOrDefault(name: String, default: String = ""): String
    fun getStringValue(name: String): String?
    fun getStringList(name: String): List<String>
    fun setBoolValue(name: String, value: Boolean)
    fun setIntValue(name: String, value: Int)
    fun setStringList(name: String, value: List<String>)
    fun setStringValue(name: String, value: String)
    fun save()
    fun filesDir(): String
}
