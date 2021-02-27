package com.github.grishberg.profiler.androidstudio

import com.github.grishberg.profiler.ui.FramesManager
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.PluginDialogFactory
import com.github.grishberg.profiler.ui.PluginFramesManager
import com.github.grishberg.profiler.ui.PluginThemeController
import com.github.grishberg.profiler.ui.ViewFactory
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfoProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
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
        val viewFactory: ViewFactory = PluginDialogFactory(project.context().adb, projectInfoProvider)
        val themeController = PluginThemeController()
        val framesManager: FramesManager = PluginFramesManager(
            settings,
            logger, themeController, viewFactory
        )
        framesManager.createMainFrame(Main.StartMode.DEFAULT)
    }
}
