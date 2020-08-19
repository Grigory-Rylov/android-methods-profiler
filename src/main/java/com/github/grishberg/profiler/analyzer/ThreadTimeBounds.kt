package com.github.grishberg.profiler.analyzer

data class ThreadTimeBounds(
    var minTime: Double = Double.MAX_VALUE,
    var maxTime: Double = 0.0
)
