package com.github.grishberg.profiler.plugins

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.plugins.stages.MethodsAvailabilityImpl
import com.github.grishberg.profiler.plugins.stages.StageAnalyzerDialog
import com.github.grishberg.profiler.plugins.stages.StagesAnalyzer
import com.github.grishberg.profiler.plugins.stages.methods.StagesAnalyzerLogic
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane

class PluginsFacade(
    private val frame: JFrame,
    private val stagesFacade: StagesFacade,
    private val systraceStagesFacade: SystraceStagesFacade,
    private val focusElementDelegate: FocusElementDelegate,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) {
    var currentThread: ThreadItem? = null
    var currentTraceProfiler: AnalyzerResult? = null

    fun createPluginsMenu(menuBar: JMenuBar) {
        val tools = JMenu("Tools")
        val stageAnalyzerMethods = JMenuItem("Stage analyzer (methods)")
        val stageAnalyzerSystrace = JMenuItem("Stage analyzer (systrace)")
        tools.add(stageAnalyzerMethods)
        tools.add(stageAnalyzerSystrace)
        menuBar.add(tools)
        stageAnalyzerMethods.addActionListener {
            runStageAnalyzerMethods()
        }
        stageAnalyzerSystrace.addActionListener {
            runStageAnalyzerSystrace()
        }

        tools.add(stagesFacade.clearStagesMenuItem)
    }

    private fun runStageAnalyzerMethods() {
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

        val ui = StageAnalyzerDialog(frame)
        val methodsAvailability = MethodsAvailabilityImpl()
        StagesAnalyzerLogic(
            StagesAnalyzer(),
            ui,
            settings,
            methods,
            focusElementDelegate,
            coroutineScope,
            dispatchers,
            stagesFacade.getStagesFactory(methodsAvailability),
            methodsAvailability,
            logger
        )
    }

    private fun runStageAnalyzerSystrace() {
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

        val ui = StageAnalyzerDialog(frame)
        val methodsAvailability = MethodsAvailabilityImpl()
        StagesAnalyzerLogic(
            analyzer = StagesAnalyzer(),
            ui = ui,
            settings = settings,
            methods = methods,
            focusElementDelegate = focusElementDelegate,
            coroutineScope = coroutineScope,
            dispatchers = dispatchers,
            stagesFactory = systraceStagesFacade.getStagesFactory(methodsAvailability),
            methodsAvailability = methodsAvailability,
            logger
        )
    }
}
