package com.github.grishberg.profiler.comparator

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
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.theme.ThemeController
import kotlinx.coroutines.*
import java.io.File

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

    fun aggregateAndCompareTraces(reference: List<File>, tested: List<File>) {
        coroutineScope.launch(dispatchers.ui) {
            refWindowController.showDialog()
            testWindowController.showDialog()

            val referenceData = reference.map { parseTraceAsync(it) }
            val testedData = tested.map { parseTraceAsync(it) }

            val aggregatedRef = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                aggregator.aggregate(referenceData.awaitAll())
            }
            val aggregatedTest = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                aggregator.aggregate(testedData.awaitAll())
            }

            withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                comparator.compare(aggregatedRef, aggregatedTest)
            }

            refWindowController.showAggregatedFlameChart(aggregatedRef)
            testWindowController.showAggregatedFlameChart(aggregatedTest)
        }
    }

    private fun parseTraceAsync(trace: File): Deferred<List<ProfileData>> {
        return coroutineScope.async(dispatchers.worker) {
            val traceContainer = TraceAnalyzer(logger).analyze(trace)
            traceContainer.mutableData[traceContainer.mainThreadId] ?: emptyList()
        }
    }
}
