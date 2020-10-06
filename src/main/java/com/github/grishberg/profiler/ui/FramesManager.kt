package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.settings.SettingsRepository
import kotlin.system.exitProcess

class FramesManager {
    private var frameInstancesCount = 0

    fun createMainFrame(
        startMode: Main.StartMode,
        settings: SettingsRepository,
        log: AppLogger
    ): Main {
        frameInstancesCount++
        return Main(startMode, settings, log, this)
    }

    fun onFrameClosed() {
        frameInstancesCount--
        if (frameInstancesCount == 0) {
            exitProcess(0)
        }
    }
}
