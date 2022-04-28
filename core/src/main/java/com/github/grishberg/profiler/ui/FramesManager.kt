package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.comparator.AggregatorMain
import com.github.grishberg.profiler.comparator.TraceComparatorApp

interface FramesManager {
    fun createMainFrame(
        startMode: Main.StartMode
    ): Main

    fun onFrameClosed()

    fun createAggregatorFrame(): AggregatorMain

    fun createComparatorFrame(): TraceComparatorApp
}
