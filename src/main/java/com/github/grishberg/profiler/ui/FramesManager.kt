package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.common.updates.StandaloneAppUpdatesChecker
import com.github.grishberg.profiler.ui.theme.StandaloneAppThemeController
import kotlin.system.exitProcess

class FramesManager(settings: SettingsRepository, log: AppLogger) {
    private var frameInstancesCount = 0
    private val versionsChecker = StandaloneAppUpdatesChecker(
        settings,
        MainScope(),
        CoroutinesDispatchersImpl(),
        log
    )

    fun createMainFrame(
        startMode: Main.StartMode,
        settings: SettingsRepository,
        log: AppLogger
    ): Main {
        frameInstancesCount++
        return Main(
            startMode, settings, log, this,
            StandaloneAppThemeController(settings),
            versionsChecker
        )
    }

    fun onFrameClosed() {
        frameInstancesCount--
        if (frameInstancesCount == 0) {
            exitProcess(0)
        }
    }
}
