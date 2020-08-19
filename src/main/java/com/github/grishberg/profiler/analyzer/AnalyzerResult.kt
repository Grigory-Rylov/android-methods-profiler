package com.github.grishberg.profiler.analyzer

data class AnalyzerResult(
    val threadTimeBounds: Map<Int, ThreadTimeBounds>,
    val globalTimeBounds: Map<Int, ThreadTimeBounds>,
    val maxLevel: Int,
    val data: MutableMap<Int, MutableList<ProfileData>>,
    val threads: List<ThreadItem>,
    val mainThreadId: Int
)
