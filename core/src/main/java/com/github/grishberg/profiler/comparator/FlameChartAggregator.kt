package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.TraceAnalyzer
import com.github.grishberg.profiler.chart.flame.FlameChartController
import com.github.grishberg.profiler.chart.flame.FlameChartDialog
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.theme.ThemeController
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.min

private const val INCLUDE_METHOD_THRESHOLD = 0.4

class FlameChartAggregator(
    private val methodsColor: MethodsColor,
    private val settings: SettingsFacade,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val themeController: ThemeController,
) {
    private val levelHeight: Double = 15.0
    private var rootLevel = 0
    private var topOffset = 0.0

    fun aggregateAndCompareTraces(reference: List<File>, tested: List<File>) {
        runBlocking {
            val referenceData = reference.map { parseTraceAsync(it) }
            val testedData = tested.map { parseTraceAsync(it) }

            aggregateAndShow(referenceData.awaitAll(), testedData.awaitAll())
        }
    }

    // TODO: support bg threads methods comparing
    private fun parseTraceAsync(trace: File): Deferred<List<ProfileData>> {
        return coroutineScope.async(dispatchers.worker) {
            val traceContainer = TraceAnalyzer(logger).analyze(trace)
            traceContainer.mutableData[traceContainer.mainThreadId] ?: emptyList()
        }
    }

    private suspend fun aggregateAndShow(
        reference: List<List<ProfileData>>,
        tested: List<List<ProfileData>>
    ) {
        val referenceReadyToAggregate = reference.map { calculateFlameToAggregateAsync(it) }
        val testedReadyToAggregate = tested.map { calculateFlameToAggregateAsync(it) }
        val referenceAggregated = aggregateAsync(referenceReadyToAggregate.awaitAll())
        val testedAggregated = aggregateAsync(testedReadyToAggregate.awaitAll())

        //  TODO: compare ref and tested

        val refWindowController = FlameChartController(methodsColor, settings, logger, coroutineScope, dispatchers)
        val testedWindowController = FlameChartController(methodsColor, settings, logger, coroutineScope, dispatchers)
        val windowRef = FlameChartDialog(refWindowController, themeController.palette, Main.DEFAULT_FOUND_INFO_MESSAGE)
        val windowTested = FlameChartDialog(testedWindowController, themeController.palette, Main.DEFAULT_FOUND_INFO_MESSAGE)
        refWindowController.foundInfoListener = windowRef
        refWindowController.dialogView = windowRef
        testedWindowController.foundInfoListener = windowTested
        testedWindowController.dialogView = windowTested
        refWindowController.showDialog()
        testedWindowController.showDialog()

        refWindowController.showAggregatedFlameChart(referenceAggregated.await(), topOffset)
        testedWindowController.showAggregatedFlameChart(testedAggregated.await(), topOffset)
    }

    private fun calculateFlameToAggregateAsync(
        threadMethods: List<ProfileData>
    ): Deferred<FlameProfileData> {
        return coroutineScope.async(dispatchers.worker) {
            return@async calculateFlameToAggregateImpl(threadMethods)
        }
    }

    private fun calculateFlameToAggregateImpl(threadMethods: List<ProfileData>): FlameProfileData {
        val rootSources = threadMethods.filter { it.level == 0 }
        val left = rootSources.minByOrNull { it.globalStartTimeInMillisecond }?.globalStartTimeInMillisecond
            ?: throw IllegalStateException("Root sources must not be empty")
        val right = rootSources.maxByOrNull { it.globalEndTimeInMillisecond }?.globalEndTimeInMillisecond
            ?: throw IllegalStateException("Root sources must not be empty")
        val width = right - left
        val fakeRoot = FlameProfileData(
            name = "INIT",
            count = 1,
            left,
            Double.MIN_VALUE,
            width
        )

        processChildrenToAggregate(rootSources, fakeRoot)

        return fakeRoot
    }

    private fun processChildrenToAggregate(
        rootSources: List<ProfileData>,
        parent: FlameProfileData
    ) {
        val children = mutableMapOf<String, ComparableFlameChildHolder>()
        var left = parent.left
        val childrenCallCount = rootSources.sumOf { it.children.size }
        for (root in rootSources) {
            for (child in root.children) {
                val start = child.globalStartTimeInMillisecond
                val childHolder = children.getOrPut(child.name) { ComparableFlameChildHolder(start) }
                childHolder.minLeft = min(childHolder.minLeft, start)
                childHolder.children.add(child)
            }
        }

        val sorted = children.toList().sortedBy { (_, value) -> value.minLeft }.toMap()

        for (entry in sorted) {
            val child = entry.value.children.first()
            val top = calculateTopForLevel(entry.value.children.first())
            val width = (parent.width * entry.value.count) / childrenCallCount
            val cmpChild = FlameProfileData(
                child.name,
                entry.value.count,
                left,
                top,
                width
            )
            parent.addChild(cmpChild)
            processChildrenToAggregate(entry.value.children, cmpChild)
            left += width
        }
    }

    private fun calculateTopForLevel(record: ProfileData): Double {
        val top = (rootLevel - record.level) * levelHeight
        if (top < topOffset) {
            topOffset = top
        }
        return top
    }

    private fun aggregateAsync(
        charts: List<FlameProfileData>
    ): Deferred<AggregatedFlameProfileData> {
        return coroutineScope.async(dispatchers.worker) {
            return@async aggregateImpl(charts)
        }
    }

    private fun aggregateImpl(charts: List<FlameProfileData>): AggregatedFlameProfileData {
        check(charts.all { it.name == "INIT" })
        val left = charts.minOf { it.left }
        val width = charts.sumOf { it.width } / charts.size

        val result = AggregatedFlameProfileData(
            name = "INIT",
            sumCountAggregated = 1,
            sumWidthAggregated = width,
            countAggregated = 1,
            left,
            Double.MIN_VALUE,
            width
        )

        processNextLayer(charts.map { it.children }, result)

        return result
    }

    private fun processNextLayer(chartLayerLists: List<List<FlameProfileData>>, parent: AggregatedFlameProfileData) {
        val traversed: Map<String, List<FlameProfileData>> = chartLayerLists.flatten().groupBy { it.name }
        val aggregated: Map<String, AggregatedFlameProfileData> = traversed.mapValues { (name, dataToAggregate) ->
            val acc = AggregatedFlameProfileData(
                name,
                sumCountAggregated = 0,
                sumWidthAggregated = 0.0,
                countAggregated = 0,
                left = Double.MAX_VALUE,
                dataToAggregate.first().top,
                width = 0.0  // will be set later
            )
            dataToAggregate.fold(acc) { current, next ->
                AggregatedFlameProfileData(
                    name,
                    current.sumCountAggregated + next.count,
                    current.sumWidthAggregated + next.width,
                    current.countAggregated + 1,
                    left = min(current.left, next.left),
                    top = current.top,
                    width = 0.0  // will be set later
                )
            }
        }

        val parentMethodTime = aggregated.values.sumByDouble { data ->
            if (data.countAggregated.toDouble() / chartLayerLists.size > INCLUDE_METHOD_THRESHOLD) {
                data.meanWidth
            } else 0.0
        }

        val sorted = aggregated.toList().sortedBy { (_, data) -> data.left }.toMap()

        var curLeft = parent.left
        for ((name, data) in sorted) {
            if (data.countAggregated.toDouble() / chartLayerLists.size <= INCLUDE_METHOD_THRESHOLD) continue
            val width = data.meanWidth * parent.width / parentMethodTime
            data.left = curLeft
            data.width = width
            parent.addChild(data)
            processNextLayer(traversed[name]!!.map { it.children }, data)
            curLeft += width
        }
    }
}
