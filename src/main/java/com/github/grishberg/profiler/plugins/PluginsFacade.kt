package com.github.grishberg.profiler.plugins

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.chart.stages.StagesFacade
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.plugins.stages.StageAnalyzerDialog
import com.github.grishberg.profiler.plugins.stages.StagesAnalyzerLogic
import com.github.grishberg.profiler.plugins.stages.StagesLoadedAction
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import javax.swing.*

class PluginsFacade(
    private val frame: JFrame,
    private val parentComponent: JComponent,
    private val stagesFacade: StagesFacade,
    private val focusElementDelegate: FocusElementDelegate,
    settings: SettingsRepository,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val stagesLoadedAction: StagesLoadedAction? = null
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
            JOptionPane.showMessageDialog(
                frame,
                "Open or record trace first".trimIndent(),
                "Stage Analyzer error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        val methods = currentTraceProfiler?.data?.get(currentThread?.threadId) ?: return

        val ui = StageAnalyzerDialog(frame, parentComponent)
        StagesAnalyzerLogic(
            ui,
            methods,
            focusElementDelegate,
            coroutineScope,
            dispatchers,
            stagesFacade.stagesList,
            stagesFacade.storedStages,
            logger,
            stagesLoadedAction
        )
    }
}
