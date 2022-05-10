package com.github.grishberg.profiler.comparator.aggregator

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.TraceAnalyzer
import com.github.grishberg.profiler.chart.flame.FlameChartController
import com.github.grishberg.profiler.chart.flame.FlameChartDialog
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.comparator.aggregator.model.AggregatedFlameProfileDataImpl
import com.github.grishberg.profiler.comparator.aggregator.threads.FlameThreadItem
import com.github.grishberg.profiler.comparator.aggregator.threads.AggregatedFlameThreadSwitchController
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.theme.ThemeController
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AggregatorMain(
    methodsColor: MethodsColor,
    settings: SettingsFacade,
    themeController: ThemeController,
    private val logger: AppLogger,
) {
    private val coroutineScope: CoroutineScope = MainScope()
    private val dispatchers: CoroutinesDispatchers = CoroutinesDispatchersImpl()
    private val aggregator = FlameChartAggregator()
    private val comparator = AggregatedFlameChartComparator()
    private val refWindowController = FlameChartController(
        methodsColor,
        settings,
        logger,
        coroutineScope,
        dispatchers,
    )
    private val testWindowController = FlameChartController(
        methodsColor,
        settings,
        logger,
        coroutineScope,
        dispatchers,
    )
    private val refWindow = FlameChartDialog(
        refWindowController,
        themeController.palette,
        Main.DEFAULT_FOUND_INFO_MESSAGE,
        RefThreadSwitchController()
    )
    private val testWindow = FlameChartDialog(
        testWindowController,
        themeController.palette,
        Main.DEFAULT_FOUND_INFO_MESSAGE,
        TestedThreadSwitchController()
    )
    private val refFlameCharts = ConcurrentHashMap<FlameThreadItem, AggregatedFlameProfileDataImpl>()
    private val testFlameCharts = ConcurrentHashMap<FlameThreadItem, AggregatedFlameProfileDataImpl>()

    init {
        refWindowController.foundInfoListener = refWindow
        refWindowController.dialogView = refWindow
        testWindowController.foundInfoListener = testWindow
        testWindowController.dialogView = testWindow
    }

    fun aggregateAndCompareTraces(
        reference: List<File>,
        tested: List<File>
    ) {
        coroutineScope.launch(dispatchers.ui) {
            refWindowController.showDialog()
            testWindowController.showDialog()
            refWindow.title = "reference"
            testWindow.title = "tested"

            val referenceTraces = reference.map { parseTraceAsync(it) }
            val testedTraces = tested.map { parseTraceAsync(it) }

            val aggregatedRef =
                withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    aggregator.aggregate(referenceTraces.awaitAll().mainThreadData(), "main")
                }
            val aggregatedTest =
                withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    aggregator.aggregate(testedTraces.awaitAll().mainThreadData(), "main")
                }

            withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                comparator.compare(aggregatedRef, aggregatedTest)
                refFlameCharts[FlameThreadItem.MAIN] = aggregatedRef
                testFlameCharts[FlameThreadItem.MAIN] = aggregatedTest
            }

            refWindowController.showAggregatedFlameChart(aggregatedRef)
            testWindowController.showAggregatedFlameChart(aggregatedTest)

            val threadsToCompare = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                aggregator.aggregateBgThreads(
                    referenceTraces.awaitAll(),
                    testedTraces.awaitAll()
                )
            }
            withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                for ((refThread, testThread) in threadsToCompare) {
                    comparator.compare(refThread, testThread)
                    refFlameCharts[FlameThreadItem(refThread.name)] = refThread
                    testFlameCharts[FlameThreadItem(testThread.name)] = testThread
                }
            }

            val refBgThreadsInOne =
                withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    aggregator.aggregateBgThreadsInOne(
                        referenceTraces.awaitAll(),
                        "all bg threads in one"
                    )
                }
            val testBgThreadsInOne =
                withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    aggregator.aggregateBgThreadsInOne(
                        testedTraces.awaitAll(),
                        "all bg threads in one"
                    )
                }
            withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                comparator.compare(refBgThreadsInOne, testBgThreadsInOne)
                refFlameCharts[FlameThreadItem.ALL_BG_THREADS] = refBgThreadsInOne
                testFlameCharts[FlameThreadItem.ALL_BG_THREADS] = testBgThreadsInOne
            }
        }
    }

    private fun parseTraceAsync(trace: File): Deferred<AnalyzerResult> {
        return coroutineScope.async(dispatchers.worker) {
            TraceAnalyzer(logger).analyze(trace)
        }
    }

    private fun List<AnalyzerResult>.mainThreadData(): List<List<ProfileData>> {
        return map { it.data[it.mainThreadId] ?: emptyList() }
    }

    private inner class RefThreadSwitchController : AggregatedFlameThreadSwitchController {
        override fun getThreads(): List<FlameThreadItem> {
            return refFlameCharts.keys().toList()
        }

        override fun onThreadSelected(thread: FlameThreadItem) {
            val selected = refFlameCharts[thread]
            if (selected != null) {
                refWindowController.showAggregatedFlameChart(selected)
            }
        }
    }

    private inner class TestedThreadSwitchController : AggregatedFlameThreadSwitchController {
        override fun getThreads(): List<FlameThreadItem> {
            return testFlameCharts.keys().toList()
        }

        override fun onThreadSelected(thread: FlameThreadItem) {
            val selected = testFlameCharts[thread]
            if (selected != null) {
                testWindowController.showAggregatedFlameChart(selected)
            }
        }
    }
}
