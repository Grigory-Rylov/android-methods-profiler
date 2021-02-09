package com.github.grishberg.profiler.analyzer

import com.github.grishberg.android.profiler.core.ThreadTimeBounds

data class ThreadTimeBoundsImpl(
    override var minTime: Double = Double.MAX_VALUE,
    override var maxTime: Double = 0.0
) : ThreadTimeBounds
