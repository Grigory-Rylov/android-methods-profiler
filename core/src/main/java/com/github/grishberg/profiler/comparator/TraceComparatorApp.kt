package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.flame.FlameChartController
import com.github.grishberg.profiler.chart.flame.FlameChartDialog
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.common.UrlOpener
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.UpdatesChecker
import com.github.grishberg.profiler.comparator.aggregator.AggregatedFlameChartComparator
import com.github.grishberg.profiler.comparator.aggregator.FlameChartAggregator
import com.github.grishberg.profiler.ui.AppIconDelegate
import com.github.grishberg.profiler.ui.FramesManager
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.Main.StartMode
import com.github.grishberg.profiler.ui.ViewFactory
import com.github.grishberg.profiler.ui.theme.ThemeController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

interface ComparatorUIListener {
    fun onCompareMenuItemClick(profileData: ProfileData)

    fun onCompareFlameChartMenuItemClick(profileData: ProfileData)

    fun onFrameSelected(frame: ProfileData?)

    fun onWindowClosed()
}

class TraceComparatorApp(
    private val settings: SettingsFacade,
    private val logger: AppLogger,
    private val framesManager: FramesManager,
    private val updatesChecker: UpdatesChecker,
    private val themeController: ThemeController,
    private val viewFactory: ViewFactory,
    private val urlOpener: UrlOpener,
    private val appIconDelegate: AppIconDelegate,
    private val methodsColorRepository: MethodsColorRepository,
    private val appFilesDir: String,
) {
    private val traceComparator = TraceComparator(logger)
    private val coroutineScope = MainScope()
    private val dispatchers = CoroutinesDispatchersImpl()
    private var referenceWindow: Main? = null
    private var testedWindow: Main? = null
    private var selectedReferenceFrame: ProfileData? = null
    private var selectedTestedFrame: ProfileData? = null

    fun createFrames(reference: String?, tested: String?) {
        referenceWindow = createWindow(StartMode.DEFAULT, ReferenceComparatorUIListener())
        if (reference != null && tested != null) {
            testedWindow = createWindow(StartMode.DEFAULT, TestedComparatorUIListener())
            val analyzerResults = mutableListOf<TraceContainer>()
            referenceWindow?.openCompareTraceFile(File(reference)) { traceContainer ->
                analyzerResults.add(0, traceContainer)
                if (analyzerResults.size == 2) {
                    onParseTracesFinished(analyzerResults)
                }
            }
            testedWindow?.openCompareTraceFile(File(tested)) { traceContainer ->
                analyzerResults.add(traceContainer)
                if (analyzerResults.size == 2) {
                    onParseTracesFinished(analyzerResults)
                }
            }
        } else if (reference != null) {
            referenceWindow?.openTraceFile(File(reference))
        }
    }

    fun compare(reference: ProfileData, tested: ProfileData) {
        referenceWindow?.selectProfileData(reference)
        referenceWindow?.fitSelectedElement()
        testedWindow?.selectProfileData(tested)
        testedWindow?.fitSelectedElement()
        val (refCompareRes, testCompareRes) = traceComparator.compare(reference, tested)
        referenceWindow?.updateCompareResult(refCompareRes)
        testedWindow?.updateCompareResult(testCompareRes)
    }

    fun compareFlameCharts(reference: ProfileData, tested: ProfileData) {
        coroutineScope.launch(dispatchers.ui) {
            val aggregator = FlameChartAggregator()
            val comparator = AggregatedFlameChartComparator()
            val refWindowController = createFlameChartController()
            val testWindowController = createFlameChartController()
            val refWindow = refWindowController.createFlameChartWindow().apply {
                title = "reference"
            }
            val testWindow = testWindowController.createFlameChartWindow().apply {
                title = "tested"
            }
            refWindowController.foundInfoListener = refWindow
            refWindowController.dialogView = refWindow
            testWindowController.foundInfoListener = testWindow
            testWindowController.dialogView = testWindow

            refWindowController.showDialog()
            testWindowController.showDialog()

            val aggregatedRef =
                withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    aggregator.aggregate(listOf(listOf(reference)), "")
                }
            val aggregatedTest =
                withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    aggregator.aggregate(listOf(listOf(tested)), "")
                }

            withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                comparator.compare(aggregatedRef, aggregatedTest)
            }

            refWindowController.showAggregatedFlameChart(aggregatedRef)
            testWindowController.showAggregatedFlameChart(aggregatedTest)
        }
    }

    private fun onParseTracesFinished(analyzerResults: List<TraceContainer>) {
        val compareResult = traceComparator.compare(analyzerResults[0].result, analyzerResults[1].result)
        referenceWindow?.highlightCompareResult(compareResult.first)
        testedWindow?.highlightCompareResult(compareResult.second)
    }

    private fun createWindow(
        startMode: StartMode,
        comparatorUIListener: ComparatorUIListener
    ): Main {
        return Main(
            startMode,
            settings,
            logger,
            framesManager,
            themeController,
            updatesChecker,
            viewFactory,
            urlOpener,
            appIconDelegate,
            methodsColorRepository,
            appFilesDir,
            comparatorUIListener
        )
    }

    private fun findAndCompare(
        node: ProfileData,
        trace: TraceContainer,
        findMode: FindMode
    )  = findAndCall(node, trace, findMode) { reference, tested ->
        compare(reference, tested)
    }

    private fun findAndCompareFlameCharts(
        node: ProfileData,
        trace: TraceContainer,
        findMode: FindMode
    ) = findAndCall(node, trace, findMode) { reference, tested ->
        compareFlameCharts(reference, tested)
    }

    private fun findAndCall(
        node: ProfileData,
        trace: TraceContainer,
        findMode: FindMode,
        onFind: (ProfileData, ProfileData) -> Unit
    ) {
        val foundNodes = TraceProfileDataFinder(trace.result).findToCompare(node)
        val error = if (foundNodes.isEmpty()) {
            "${node.name} not found on this trace. " +
                    "Try select it manually and repeat compare"
        } else if (foundNodes.size > 1) {
            "Found ${foundNodes.size} same calls on this trace. " +
                    "Select one you want to compare with manually and repeat compare"
        } else ""

        if (error.isNotEmpty()) {
            val window = if (findMode == FindMode.FIND_TESTED) testedWindow else referenceWindow
            window?.showErrorDialog("Find ${node.name}", error)
        } else {
            val foundNodeInfo = foundNodes.first()
            val reference = if (findMode == FindMode.FIND_TESTED) node else foundNodeInfo.node
            val tested = if (findMode == FindMode.FIND_REFERENCE) node else foundNodeInfo.node
            val windowFindIn = if (findMode == FindMode.FIND_TESTED) testedWindow else referenceWindow
            windowFindIn?.switchThread(foundNodeInfo.thread)

            onFind(reference, tested)
        }
    }

    private fun createFlameChartController(): FlameChartController {
        return FlameChartController(
            MethodsColorImpl(methodsColorRepository),
            settings,
            logger,
            coroutineScope,
            dispatchers,
        )
    }

    private fun FlameChartController.createFlameChartWindow(): FlameChartDialog {
        return FlameChartDialog(
            this,
            themeController.palette,
            Main.DEFAULT_FOUND_INFO_MESSAGE,
        )
    }

    private enum class FindMode {
        FIND_REFERENCE,
        FIND_TESTED
    }

    private inner class ReferenceComparatorUIListener : ComparatorUIListener {

        private fun onMenuItemClick(
            profileData: ProfileData,
            onBothSelected: (ProfileData, ProfileData) -> Unit,
            onTestedNotSelected: (ProfileData, TraceContainer, FindMode) -> Unit
        ) {
            selectedTestedFrame?.let { tested ->
                onBothSelected(profileData, tested)
                return
            }

            if (testedWindow == null) {
                testedWindow = createWindow(StartMode.OPEN_TRACE_FILE, TestedComparatorUIListener())
            }

            assert(testedWindow != null) {
                "Window should be created on main thread"
            }

            var traceContainer = testedWindow?.resultContainer

            if (traceContainer == null) {
                testedWindow?.showOpenFileChooser()
                traceContainer = testedWindow?.resultContainer
            }

            if (traceContainer != null) {
                onTestedNotSelected(profileData, traceContainer, FindMode.FIND_TESTED)
            }
        }

        override fun onCompareMenuItemClick(profileData: ProfileData) =
            onMenuItemClick(
                profileData,
                this@TraceComparatorApp::compare,
                this@TraceComparatorApp::findAndCompare
            )

        override fun onCompareFlameChartMenuItemClick(profileData: ProfileData) =
            onMenuItemClick(
                profileData,
                this@TraceComparatorApp::compareFlameCharts,
                this@TraceComparatorApp::findAndCompareFlameCharts,
            )

        override fun onFrameSelected(frame: ProfileData?) {
            selectedReferenceFrame = frame
        }

        override fun onWindowClosed() {
            selectedReferenceFrame = null
            referenceWindow = null
            exitProcess(0)
        }
    }

    private inner class TestedComparatorUIListener : ComparatorUIListener {

        private fun onMenuItemClick(
            profileData: ProfileData,
            onBothSelected: (ProfileData, ProfileData) -> Unit,
            onReferenceNotSelected: (ProfileData, TraceContainer, FindMode) -> Unit
        ) {
            val reference = selectedReferenceFrame
            if (reference != null) {
                onBothSelected(reference, profileData)
                return
            }

            assert(referenceWindow != null) {
                "Tested window cannot be open when reference closed"
            }

            var traceContainer = referenceWindow?.resultContainer

            if (traceContainer == null) {
                referenceWindow?.showOpenFileChooser()
                traceContainer = referenceWindow?.resultContainer
            }

            if (traceContainer != null) {
                onReferenceNotSelected(profileData, traceContainer, FindMode.FIND_REFERENCE)
            }
        }

        override fun onCompareMenuItemClick(profileData: ProfileData) =
            onMenuItemClick(
                profileData,
                this@TraceComparatorApp::compare,
                this@TraceComparatorApp::findAndCompare,
            )

        override fun onCompareFlameChartMenuItemClick(profileData: ProfileData) =
            onMenuItemClick(
                profileData,
                this@TraceComparatorApp::compareFlameCharts,
                this@TraceComparatorApp::findAndCompareFlameCharts,
            )

        override fun onFrameSelected(frame: ProfileData?) {
            selectedTestedFrame = frame
        }

        override fun onWindowClosed() {
            selectedTestedFrame = null
            testedWindow = null
        }
    }
}
