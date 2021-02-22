package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.StandaloneAppUrlOpener
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.StandaloneAppUpdatesChecker
import com.github.grishberg.profiler.ui.theme.StandaloneAppThemeController
import kotlin.system.exitProcess

class StandaloneAppFramesManagerFramesManager(
    private val settings: SettingsFacade,
    private val log: AppLogger,
    private val dialogFactory: ViewFactory
) : FramesManager {
    private val urlOpener = StandaloneAppUrlOpener()
    private val iconDelegate = StandaloneAppIconDelegate()
    private var frameInstancesCount = 0
    private val versionsChecker = StandaloneAppUpdatesChecker(
        settings,
        MainScope(),
        CoroutinesDispatchersImpl(),
        log
    )

    override fun createMainFrame(
        startMode: Main.StartMode
    ): Main {
        frameInstancesCount++
        return Main(
            startMode, settings, log, this,
            StandaloneAppThemeController(settings),
            versionsChecker,
            dialogFactory,
            urlOpener,
            iconDelegate
        )
    }

    override fun onFrameClosed() {
        frameInstancesCount--
        if (frameInstancesCount == 0) {
            exitProcess(0)
        }
    }
}
