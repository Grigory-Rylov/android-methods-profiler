package com.github.grishberg.profiler.ui

interface FramesManager {
    fun createMainFrame(
        startMode: Main.StartMode
    ): Main

    fun onFrameClosed()
}
