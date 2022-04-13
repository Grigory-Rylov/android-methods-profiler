package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.common.TraceContainer

fun interface OpenTraceToCompareCallback {
    fun onTraceOpened(traceContainer: TraceContainer)
}
