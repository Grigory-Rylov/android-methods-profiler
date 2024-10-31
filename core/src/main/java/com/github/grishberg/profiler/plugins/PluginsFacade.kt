package com.github.grishberg.profiler.plugins

import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ThreadItem
import com.github.grishberg.profiler.plugins.stages.MethodsAvailabilityImpl
import com.github.grishberg.profiler.plugins.stages.StageAnalyzerDialog
import com.github.grishberg.profiler.plugins.stages.StagesAnalyzer
import com.github.grishberg.profiler.plugins.stages.constructors.EarlyConstructorsLogicImpl
import com.github.grishberg.profiler.plugins.stages.constructors.EarlyConstructorsSearchDialog
import com.github.grishberg.profiler.plugins.stages.methods.StagesAnalyzerLogic
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import javax.swing.*

class PluginsFacade(
    private val frame: JFrame,
    private val stagesFacade: StagesFacade,
    private val systraceStagesFacade: SystraceStagesFacade,
    private val focusElementDelegate: FocusElementDelegate,
    private val settings: SettingsFacade,
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
        val earlyConstructorsAnalyzer = JMenuItem("Early constructors analyzer")

        tools.add(earlyConstructorsAnalyzer)
        tools.add(stageAnalyzerMethods)
        tools.add(stageAnalyzerSystrace)
        menuBar.add(tools)

        earlyConstructorsAnalyzer.addActionListener {
            runEarlyConstructorsAnalyzer()
        }

        stageAnalyzerMethods.addActionListener {
            runStageAnalyzerMethods()
        }
        stageAnalyzerSystrace.addActionListener {
            runStageAnalyzerSystrace()
        }

        tools.add(stagesFacade.clearStagesMenuItem)
    }

    private fun runEarlyConstructorsAnalyzer() {
        if (currentTraceProfiler == null || currentThread == null) {
            JOptionPane.showMessageDialog(
                frame,
                "Open or record trace first".trimIndent(),
                "Stage Analyzer error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        val allThreadsMethods = currentTraceProfiler?.data ?: return
        val currentThreadId = currentThread?.threadId ?: return
        val currentThreadMethods = allThreadsMethods.get(currentThreadId) ?: return

        val methodsAvailability = MethodsAvailabilityImpl()
        val stagesFactory = stagesFacade.getStagesFactory(methodsAvailability)

        val logic = EarlyConstructorsLogicImpl(
            settings = settings,
            coroutineScope = coroutineScope,
            dispatchers = dispatchers,
            stagesFacade = stagesFacade,
            stagesFactory = stagesFactory,
            allMethods = allThreadsMethods,
            currentThreadId = currentThreadId,
            methods = currentThreadMethods,
            focusElementDelegate = focusElementDelegate,
        )

        val ui = EarlyConstructorsSearchDialog(frame, logic)
        logic.ui = ui

        if (stagesFacade.stagesList.isNotEmpty()){
            ui.setStages(stagesFacade.stagesList)
        }
        ui.show(frame)
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
        val allThreadsMethods = currentTraceProfiler?.data ?: return
        val currentThreadId = currentThread?.threadId ?: return
        val currentThreadMethods = allThreadsMethods.get(currentThreadId) ?: return

        val ui = StageAnalyzerDialog(frame)
        val methodsAvailability = MethodsAvailabilityImpl()
        StagesAnalyzerLogic(
            StagesAnalyzer(),
            ui,
            settings,
            allThreadsMethods,
            currentThreadId,
            currentThreadMethods,
            focusElementDelegate,
            coroutineScope,
            dispatchers,
            stagesFacade.getStagesFactory(methodsAvailability),
            methodsAvailability,
            stagesFacade,
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

        val allThreadsMethods = currentTraceProfiler?.data ?: return
        val currentThreadId = currentThread?.threadId ?: return
        val currentThreadMethods = allThreadsMethods.get(currentThreadId) ?: return

        val ui = StageAnalyzerDialog(frame)
        val methodsAvailability = MethodsAvailabilityImpl()
        StagesAnalyzerLogic(
            analyzer = StagesAnalyzer(),
            ui = ui,
            settings = settings,
            allThreadMethods = allThreadsMethods,
            currentThreadId = currentThreadId,
            currentThreadMethod = currentThreadMethods,
            focusElementDelegate = focusElementDelegate,
            coroutineScope = coroutineScope,
            dispatchers = dispatchers,
            stagesFactory = systraceStagesFacade.getStagesFactory(methodsAvailability),
            methodsAvailability = methodsAvailability,
            stagesFacade = stagesFacade,
            logger = logger
        )
    }
}
