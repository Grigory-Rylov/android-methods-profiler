package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.comparator.model.AggregatedFlameProfileDataImpl
import com.github.grishberg.profiler.comparator.model.ComparableFlameChildHolder
import com.github.grishberg.profiler.comparator.model.FlameProfileData
import kotlin.math.min

private const val INCLUDE_METHOD_THRESHOLD = 0.4
private const val THREAD_METHODS_TIMEOUT_THRESHOLD_MS = 50

class FlameChartAggregator {
    private val levelHeight: Double = 15.0
    private var rootLevel = 0
    private var topOffset = 0.0

    /**
     * Should be called from worker thread.
     */
    fun aggregate(
        threadMethods: List<List<ProfileData>>,
        threadName: String
    ): AggregatedFlameProfileDataImpl {
        val threadMethodsReadyToAggregate = threadMethods.map {
            calculateFlameToAggregate(it, threadName)
        }

        return aggregateFlameCharts(threadMethodsReadyToAggregate, threadName)
    }

    fun aggregateBgThreads(
        reference: List<AnalyzerResult>,
        tested: List<AnalyzerResult>
    ): List<Pair<AggregatedFlameProfileDataImpl, AggregatedFlameProfileDataImpl>> {
        val result = mutableListOf<Pair<AggregatedFlameProfileDataImpl, AggregatedFlameProfileDataImpl>>()
        // Suppose traces has same threads.
        val names = reference.first().threads.map { it.name }.toSet()

        for (name in names) {
            val flameChartsRef = getTracesThreadMethods(reference, name).map {
                calculateFlameToAggregate(it, "reference: $name")
            }
            val aggregatedRef = aggregateFlameCharts(flameChartsRef, "reference: $name")

            if (aggregatedRef.width < THREAD_METHODS_TIMEOUT_THRESHOLD_MS) {
                continue
            }

            val flameChartsTest = getTracesThreadMethods(tested, name).map {
                calculateFlameToAggregate(it, "tested: $name")
            }
            val aggregatedTest = aggregateFlameCharts(flameChartsTest, "tested: $name")

            if (aggregatedTest.width < THREAD_METHODS_TIMEOUT_THRESHOLD_MS) {
                continue
            }

            result.add(aggregatedRef to aggregatedTest)
        }

        return result
    }

    private fun getTracesThreadMethods(
        traces: List<AnalyzerResult>,
        threadName: String
    ): List<List<ProfileData>> {
        val result = mutableListOf<List<ProfileData>>()
        for (trace in traces) {
            val ids = trace.threads.findAllOf({ it.name == threadName }) { it.threadId }
            val mergedThreadMethods = ids.map { trace.data[it] ?: emptyList() }.flatten()

            result.add(mergedThreadMethods)
        }
        return result
    }

    fun aggregateBgThreadsInOne(
        traces: List<AnalyzerResult>,
        threadName: String
    ): AggregatedFlameProfileDataImpl {
        val bgThreadsInOne = traces.map { trace ->
            val bgMethods = mutableListOf<ProfileData>()
            for (thread in trace.threads) {
                if (thread.threadId == trace.mainThreadId) {
                    continue
                }
                bgMethods.addAll(trace.data[thread.threadId]!!)
            }
            bgMethods
        }

        val bgThreadsInOneReadyToAggregate = bgThreadsInOne.map {
            calculateFlameToAggregate(it, threadName)
        }

        return aggregateFlameCharts(bgThreadsInOneReadyToAggregate, threadName)
    }

    private fun calculateFlameToAggregate(
        threadMethods: List<ProfileData>,
        threadName: String
    ): FlameProfileData {
        val rootSources = threadMethods.filter { it.level == 0 }
        if (rootSources.isEmpty()) {
            return FlameProfileData(threadName, 1, 0.0, Double.MIN_VALUE, 0.0)
        }
        val left = rootSources.minOf { it.globalStartTimeInMillisecond }
        val width = rootSources.sumOf {
            it.globalEndTimeInMillisecond - it.globalStartTimeInMillisecond
        }
        val fakeRoot = FlameProfileData(
            name = threadName,
            count = 1,
            left,
            Double.MIN_VALUE,
            width
        )
        val fakeParent = object : ProfileData {
            override val children = rootSources
            override val globalEndTimeInMillisecond = Double.MAX_VALUE
            override val globalSelfTime = Double.MAX_VALUE
            override val globalStartTimeInMillisecond = Double.MIN_VALUE
            override val level = -1
            override val name = "fake parent"
            override val parent = null
            override val threadEndTimeInMillisecond = Double.MAX_VALUE
            override val threadSelfTime = Double.MAX_VALUE
            override val threadStartTimeInMillisecond = Double.MIN_VALUE
        }

        processChildrenToAggregate(listOf(fakeParent), fakeRoot)

        return fakeRoot
    }

    private fun processChildrenToAggregate(
        rootSources: List<ProfileData>,
        parent: FlameProfileData
    ) {
        val children = mutableMapOf<String, ComparableFlameChildHolder>()
        var left = parent.left
        for (root in rootSources) {
            for (child in root.children) {
                val start = child.globalStartTimeInMillisecond
                val childHolder = children.getOrPut(child.name) {
                    ComparableFlameChildHolder(start)
                }
                childHolder.minLeft = min(childHolder.minLeft, start)
                childHolder.children.add(child)
            }
        }

        val sorted = children.toList().sortedBy { (_, value) -> value.minLeft }.toMap()

        val childrenCallTimeout = sorted.toList().sumOf { (_, value) -> value.width }

        for (entry in sorted) {
            val child = entry.value.children.first()
            val top = calculateTopForLevel(entry.value.children.first())
            val width = (parent.width * entry.value.width) / childrenCallTimeout
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

    private fun aggregateFlameCharts(
        charts: List<FlameProfileData>,
        threadName: String
    ): AggregatedFlameProfileDataImpl {
        val left = charts.minOf { it.left }
        val width = charts.sumOf { it.width } / charts.size

        val result = AggregatedFlameProfileDataImpl(
            name = threadName,
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

    private fun processNextLayer(
        chartLayerLists: List<List<FlameProfileData>>,
        parent: AggregatedFlameProfileDataImpl
    ) {
        val traversed = chartLayerLists.flatten().groupBy { it.name }
        val aggregated = traversed.mapValues { (name, dataToAggregate) ->
            val acc = AggregatedFlameProfileDataImpl(
                name,
                sumCountAggregated = 0,
                sumWidthAggregated = 0.0,
                countAggregated = 0,
                left = Double.MAX_VALUE,
                dataToAggregate.first().top,
                width = 0.0  // will be set later
            )
            dataToAggregate.fold(acc) { current, next ->
                AggregatedFlameProfileDataImpl(
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
            if (data.countAggregated.toDouble() / chartLayerLists.size <= INCLUDE_METHOD_THRESHOLD) {
                continue
            }
            val width = data.meanWidth * parent.width / parentMethodTime
            data.left = curLeft
            data.width = width
            parent.addChild(data)
            processNextLayer(traversed[name]!!.map { it.children }, data)
            curLeft += width
        }
    }
}
