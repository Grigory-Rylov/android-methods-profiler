package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.common.updates.StandaloneAppUpdatesChecker
import com.github.grishberg.profiler.ui.theme.StandaloneAppThemeController
import kotlin.system.exitProcess

class FramesManager(
    private val settings: SettingsRepository,
    private val log: AppLogger
) {
    private var frameInstancesCount = 0
    private val versionsChecker = StandaloneAppUpdatesChecker(
        settings,
        MainScope(),
        CoroutinesDispatchersImpl(),
        log
    )

    fun createMainFrame(
        startMode: Main.StartMode
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
