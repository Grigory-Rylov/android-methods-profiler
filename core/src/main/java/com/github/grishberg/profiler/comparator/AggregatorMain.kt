package com.github.grishberg.profiler.comparator

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
import com.github.grishberg.profiler.comparator.model.AggregatedFlameProfileDataImpl
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.theme.ThemeController
import kotlinx.coroutines.*
import java.io.File

class AggregatorMain(
    private val methodsColor: MethodsColor,
    private val settings: SettingsFacade,
    private val themeController: ThemeController,
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
    )
    private val testWindow = FlameChartDialog(
        testWindowController,
        themeController.palette,
        Main.DEFAULT_FOUND_INFO_MESSAGE,
    )

    init {
        refWindowController.foundInfoListener = refWindow
        refWindowController.dialogView = refWindow
        testWindowController.foundInfoListener = testWindow
        testWindowController.dialogView = testWindow
    }

    fun aggregateAndCompareTraces(
        reference: List<File>,
        tested: List<File>,
        compareBgThreads: Boolean = false
    ) {
        coroutineScope.launch(dispatchers.ui) {
            refWindowController.showDialog()
            testWindowController.showDialog()

            val referenceTraces = reference.map { parseTraceAsync(it) }
            val testedTraces = tested.map { parseTraceAsync(it) }

            val aggregatedRef = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                aggregator.aggregate(referenceTraces.awaitAll().mainThreadData(), "reference: main")
            }
            val aggregatedTest = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                aggregator.aggregate(testedTraces.awaitAll().mainThreadData(), "tested: main")
            }

            withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                comparator.compare(aggregatedRef, aggregatedTest)
            }

            refWindowController.showAggregatedFlameChart(aggregatedRef)
            testWindowController.showAggregatedFlameChart(aggregatedTest)

            if (compareBgThreads) {
                val threadsToCompare = aggregator.aggregateBgThreads(
                    referenceTraces.awaitAll(),
                    testedTraces.awaitAll()
                )

                for ((refThread, testThread) in threadsToCompare) {
                    compareAndShowAggregatedFlameCharts(refThread, testThread)
                }

                val refBgThreadsInOne = aggregator.aggregateBgThreadsInOne(
                    referenceTraces.awaitAll(),
                    "reference: all bg threads in one"
                )
                val testBgThreadsInOne = aggregator.aggregateBgThreadsInOne(
                    testedTraces.awaitAll(),
                    "tested: all bg threads in one"
                )
                compareAndShowAggregatedFlameCharts(refBgThreadsInOne, testBgThreadsInOne)
            }
        }
    }

    private suspend fun compareAndShowAggregatedFlameCharts(
        reference: AggregatedFlameProfileDataImpl,
        tested: AggregatedFlameProfileDataImpl
    ) {
        val refThreadWindowController = FlameChartController(
            methodsColor, settings, logger, coroutineScope, dispatchers)
        val testThreadWindowController = FlameChartController(
            methodsColor, settings, logger, coroutineScope, dispatchers)
        val refThreadWindow = FlameChartDialog(
            refThreadWindowController, themeController.palette, Main.DEFAULT_FOUND_INFO_MESSAGE)
        val testThreadWindow = FlameChartDialog(
            testThreadWindowController, themeController.palette, Main.DEFAULT_FOUND_INFO_MESSAGE)
        refThreadWindowController.dialogView = refThreadWindow
        refThreadWindowController.foundInfoListener = refThreadWindow
        testThreadWindowController.dialogView = testThreadWindow
        testThreadWindowController.foundInfoListener = testThreadWindow
        refThreadWindowController.showDialog()
        testThreadWindowController.showDialog()

        withContext(coroutineScope.coroutineContext + dispatchers.worker) {
            comparator.compare(reference, tested)
        }

        refThreadWindowController.showAggregatedFlameChart(reference)
        testThreadWindowController.showAggregatedFlameChart(tested)
    }

    private fun parseTraceAsync(trace: File): Deferred<AnalyzerResult> {
        return coroutineScope.async(dispatchers.worker) {
            TraceAnalyzer(logger).analyze(trace)
        }
    }

    private fun List<AnalyzerResult>.mainThreadData(): List<List<ProfileData>> {
        return map { it.data[it.mainThreadId] ?: emptyList() }
    }
}
