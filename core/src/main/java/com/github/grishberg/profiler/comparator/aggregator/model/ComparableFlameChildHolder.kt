package com.github.grishberg.profiler.comparator.aggregator.model

import com.github.grishberg.profiler.core.ProfileData
import kotlin.math.max

data class ComparableFlameChildHolder(
    var minLeft: Double,
    val children: MutableList<ProfileData> = mutableListOf()
) {
    val count get() = children.size
    val width get() = children.sumOf {
        max(it.globalEndTimeInMillisecond - it.globalStartTimeInMillisecond, 0.0)
    } / count
}
