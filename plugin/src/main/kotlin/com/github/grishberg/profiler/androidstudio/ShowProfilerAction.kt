package com.github.grishberg.profiler.androidstudio

import com.github.grishberg.profiler.chart.highlighting.ColorInfoAdapter
import com.github.grishberg.profiler.chart.highlighting.PluginMethodsColorRepository
import com.github.grishberg.profiler.ui.FramesManager
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.NotificationHelperImpl
import com.github.grishberg.profiler.ui.PluginDialogFactory
import com.github.grishberg.profiler.ui.PluginFramesManager
import com.github.grishberg.profiler.ui.PluginThemeController
import com.github.grishberg.profiler.ui.ViewFactory
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfoProvider
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

class ShowProfilerAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        actionPerformed(e, e.getData(PlatformDataKeys.PROJECT)!!)
    }

    private fun actionPerformed(e: AnActionEvent, project: Project) {
        val settings: PluginState = StorageService.getInstance().state ?: PluginState()
        settings.updateBaseDir(project)

        val logger = PluginLogger()
        val projectInfo = PluginProjectInfo(project, logger)
        val projectInfoProvider = ProjectInfoProvider(projectInfo, settings)
        val methodsColorRepository = PluginMethodsColorRepository(settings, ColorInfoAdapter(logger))
        val adbWrapper = AsAdbWrapper(project, PluginLogger())
        val viewFactory: ViewFactory =
            PluginDialogFactory(adbWrapper, projectInfoProvider, methodsColorRepository)
        val themeController = PluginThemeController()
        val framesManager: FramesManager = PluginFramesManager(
            settings,
            logger, themeController, viewFactory,
            methodsColorRepository
        )
        framesManager.createMainFrame(Main.StartMode.DEFAULT)

        showSupportBannerIfNeeded(settings, NotificationHelperImpl(project))
    }

    private fun showSupportBannerIfNeeded(settings: PluginState, notificationHelper: NotificationHelperImpl) {
        val plugin =
            PluginManagerCore.getPlugin(PluginId.getId("com.github.grishberg.android.android-layout-inspector-plugin"))
                ?: return
        if (plugin.version != settings.lastVersion) {
            settings.lastVersion = plugin.version
            notificationHelper.supportInfo(
                "Support me if you like YAMP =)",
                "BNB,ETH tokens : 0x25Ca16AD3c4e9BD1e6e5FDD77eDB019386B68591\n\n" +
                    "USDT TRC20 : TSo3X6K54nYq3S64wML4M4xFgTNiENkHwC\n\n" +
                    "BTC : bc1qmm5lp389scuk2hghgyzdztddwgjnxqa2awrrue\n\n" +
                    "https://www.tinkoff.ru/cf/4KNjR2SMOAj"
            )
        }
    }
}
