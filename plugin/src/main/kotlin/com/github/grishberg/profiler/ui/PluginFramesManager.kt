package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.UrlOpener
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.UpdatesChecker
import com.github.grishberg.profiler.ui.theme.ThemeController
import java.awt.Frame

class PluginFramesManager(
    private val settingsFacade: SettingsFacade,
    private val logger: AppLogger,
    private val themesController: ThemeController,
    private val viewFactory: ViewFactory
) : FramesManager {
    private val urlOpener = object : UrlOpener {
        override fun openUrl(uri: String) = Unit
    }
    private val iconDelegate = PluginAppIconDelegate()

    override fun createMainFrame(startMode: Main.StartMode): Main {
        return Main(
            startMode, settingsFacade, logger, this,
            themesController, NoOpUpdatesChecker, viewFactory, urlOpener,
            iconDelegate
        )
    }

    override fun onFrameClosed() = Unit

    object NoOpUpdatesChecker : UpdatesChecker {
        override var checkForUpdatesState = false
        override fun checkForUpdates(callback: UpdatesChecker.UpdatesFoundAction) = Unit
        override fun shouldAddToMenu() = false
    }
}
