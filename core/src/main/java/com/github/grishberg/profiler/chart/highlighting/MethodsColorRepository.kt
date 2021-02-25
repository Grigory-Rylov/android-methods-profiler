package com.github.grishberg.profiler.chart.highlighting

/**
 * Contains methods colors.
 */
interface MethodsColorRepository {
    /**
     * Returns stored colors.
     */
    fun getColors(): List<ColorInfo>

    /**
     * Updates colors.
     */
    fun updateColors(colors: List<ColorInfo>)
}
