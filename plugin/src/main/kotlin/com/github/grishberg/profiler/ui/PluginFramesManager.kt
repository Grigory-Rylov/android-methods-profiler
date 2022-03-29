package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.androidstudio.PluginState
import com.github.grishberg.profiler.chart.highlighting.ColorInfoAdapter
import com.github.grishberg.profiler.chart.highlighting.PluginMethodsColorRepository
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.UrlOpener
import com.github.grishberg.profiler.common.updates.UpdatesChecker
import com.github.grishberg.profiler.ui.theme.ThemeController

class PluginFramesManager(
    private val settingsFacade: PluginState,
    private val logger: AppLogger,
    private val themesController: ThemeController,
    private val viewFactory: ViewFactory,
    private val methodsColorRepository: PluginMethodsColorRepository
) : FramesManager {
    private val urlOpener = object : UrlOpener {
        override fun openUrl(uri: String) = Unit
    }
    private val iconDelegate = PluginAppIconDelegate()

    override fun createMainFrame(startMode: Main.StartMode): Main {
        return Main(
            startMode, settingsFacade, logger, this,
            themesController, NoOpUpdatesChecker, viewFactory, urlOpener,
            iconDelegate,
            methodsColorRepository,
            "<not used>",
            false
        )
    }

    override fun onFrameClosed() = Unit

    object NoOpUpdatesChecker : UpdatesChecker {
        override var checkForUpdatesState = false
        override fun checkForUpdates(callback: UpdatesChecker.UpdatesFoundAction) = Unit
        override fun shouldAddToMenu() = false
    }
}
