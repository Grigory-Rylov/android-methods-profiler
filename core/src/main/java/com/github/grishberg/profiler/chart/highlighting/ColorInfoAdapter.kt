package com.github.grishberg.profiler.chart.highlighting

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.toHex
import java.awt.Color

private const val TAG = "ColorInfoAdapter"

const val FILTER_KEY = "filter"
const val COLOR_IN_HEX_KEY = "color"

class ColorInfoAdapter(
    private val log: AppLogger
) {
    fun stringsToColorInfo(colors: List<Map<String, String>>): List<ColorInfo> {
        val result = mutableListOf<ColorInfo>()
        for (color in colors) {
            val filter = color[FILTER_KEY]
            val colorInHex = color[COLOR_IN_HEX_KEY]
            if (filter == null || filter == "" || colorInHex == null || colorInHex == "") {
                continue
            }
            try {
                val colorInt = Integer.parseInt(colorInHex, 16)
                val parsedColor = Color(colorInt) ?: continue
                result.add(ColorInfo(filter, parsedColor))
            } catch (e: NumberFormatException) {
                log.e("$TAG: recognize color error: $filter - $colorInHex")
            }
        }
        return result.toList()
    }

    fun colorInfoToStrings(colors: List<ColorInfo>): List<Map<String, String>> {
        val res = mutableListOf<Map<String, String>>()
        for (color in colors) {
            res.add(mapOf(Pair(FILTER_KEY, color.filter), Pair(COLOR_IN_HEX_KEY, color.color.toHex())))
        }
        return res
    }
}
