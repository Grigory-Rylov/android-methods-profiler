package com.github.grishberg.profiler.analyzer

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData

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
