package com.github.grishberg.profiler.ui

object DurationPercentFormatter {
    @JvmStatic
    fun durationTextWithParentPercent(duration: Double, parentDuration: Double): String {
        if (parentDuration <= 0.0) {
            return String.format("%.3f ms", duration)
        }
        val percent = duration / parentDuration * 100.0
        return String.format("%.3f ms (%.1f%%)", duration, percent)
    }
}
