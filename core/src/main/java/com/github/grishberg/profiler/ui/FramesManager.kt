package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.comparator.AggregatorMain

interface FramesManager {
    fun createMainFrame(
        startMode: Main.StartMode
    ): Main

    fun onFrameClosed()

    fun createAggregatorFrame(): AggregatorMain
}
