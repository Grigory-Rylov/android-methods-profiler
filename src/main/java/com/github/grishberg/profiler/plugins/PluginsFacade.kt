package com.github.grishberg.profiler.plugins

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.plugins.stages.StageAnalyzerDialog
import com.github.grishberg.profiler.plugins.stages.StagesAnalyzerLogic
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem

class PluginsFacade(
    private val frame: JFrame,
    private val focusElementDelegate: FocusElementDelegate,
    settings: SettingsRepository,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) {
    var currentThread: ThreadItem? = null
    var currentTraceProfiler: AnalyzerResult? = null

    fun createPluginsMenu(menuBar: JMenuBar) {
        val tools = JMenu("Tools")
        val stageAnalyzer = JMenuItem("Stage analyzer")
        tools.add(stageAnalyzer)
        menuBar.add(tools)
        stageAnalyzer.addActionListener {
            runStageAnalyzer()
        }
    }

    private fun runStageAnalyzer() {
        if (currentTraceProfiler == null || currentThread == null) {
            return
        }
        val ui = StageAnalyzerDialog(frame)
        StagesAnalyzerLogic(
            frame, ui, logger,
            currentTraceProfiler!!, currentThread!!,
            focusElementDelegate,
            coroutineScope,
            dispatchers
        )
    }
}
