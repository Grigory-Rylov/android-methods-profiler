package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ProfileData
import kotlin.math.max

data class AnalyzerResultImpl(
    override val threadTimeBounds: Map<Int, ThreadTimeBoundsImpl>,
    override val globalTimeBounds: Map<Int, ThreadTimeBoundsImpl>,
    override val maxLevel: Int,
    internal val mutableData: MutableMap<Int, MutableList<ProfileDataImpl>>,
    override val threads: List<ThreadItemImpl>,
    override val mainThreadId: Int,
    override val startTimeUs: Long = -1,
) : AnalyzerResult {
    override val data: Map<Int, List<ProfileData>>
        get() = mutableData
}

/**
 * Calculates maximum global time in trace.
 */
fun AnalyzerResult.calculateMaxGlobalTime(): Double {
    var maxGlobalTime = 0.0
    for (bound in this.globalTimeBounds) {
        maxGlobalTime = max(maxGlobalTime, bound.value.maxTime)
    }
    return maxGlobalTime
}

fun AnalyzerResult.calculateMaxThreadTime(): Double {
    var maxThreadTime = 0.0
    for (bound in this.threadTimeBounds) {
        maxThreadTime = max(maxThreadTime, bound.value.maxTime)
    }
    return maxThreadTime
}
