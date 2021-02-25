package com.github.grishberg.profiler.chart.highlighting

import com.github.grishberg.profiler.androidstudio.PluginState
import com.google.gson.GsonBuilder

class PluginMethodsColorRepository(
    private val pluginState: PluginState,
    private val colorsInfoAdapter: ColorInfoAdapter
) : MethodsColorRepository {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()
    private val token: List<Map<String, String>> = emptyList()

    override fun getColors(): List<ColorInfo> {
        val data = gson.fromJson(pluginState.methodColors, token::class.java)
        return colorsInfoAdapter.stringsToColorInfo(data)
    }

    override fun updateColors(colors: List<ColorInfo>) {
        val strings = colorsInfoAdapter.colorInfoToStrings(colors)
        pluginState.methodColors = gson.toJson(strings)
    }
}
