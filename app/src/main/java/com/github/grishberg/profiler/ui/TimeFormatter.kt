package com.github.grishberg.profiler.ui

import java.text.SimpleDateFormat
import java.util.*

class TimeFormatter {
    private val sdf = SimpleDateFormat("mm:ss.SSS")

    fun timeToString(ms: Double) : String {
        if (ms <1.0 && ms > 0.0) {
            val microPart = ((ms * 1000) % 1000).toInt()
            return "$microPart μs"
        }
        return sdf.format(Date(ms.toLong()))
    }
}