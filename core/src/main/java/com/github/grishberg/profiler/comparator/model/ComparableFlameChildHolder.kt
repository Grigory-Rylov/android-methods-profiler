package com.github.grishberg.profiler.comparator.model

import com.github.grishberg.android.profiler.core.ProfileData

data class ComparableFlameChildHolder(
    var minLeft: Double,
    val children: MutableList<ProfileData> = mutableListOf()
) {
    val count get() = children.size
    val width get() = children.sumOf {
        it.globalEndTimeInMillisecond - it.globalStartTimeInMillisecond
    } / count
}
