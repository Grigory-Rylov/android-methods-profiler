package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import kotlin.math.min

private const val INCLUDE_METHOD_THRESHOLD = 0.4

class FlameChartAggregator {
    private val levelHeight: Double = 15.0
    private var rootLevel = 0
    private var topOffset = 0.0

    /**
     * Should be called from worker thread.
     */
    fun aggregate(
        reference: List<List<ProfileData>>,
        tested: List<List<ProfileData>>
    ): Pair<AggregatedFlameProfileData, AggregatedFlameProfileData> {
        val referenceReadyToAggregate = reference.map { calculateFlameToAggregate(it) }
        val testedReadyToAggregate = tested.map { calculateFlameToAggregate(it) }
        val referenceAggregated = aggregate(referenceReadyToAggregate)
        val testedAggregated = aggregate(testedReadyToAggregate)

        return referenceAggregated to testedAggregated
    }

    private fun calculateFlameToAggregate(threadMethods: List<ProfileData>): FlameProfileData {
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

    private fun aggregate(charts: List<FlameProfileData>): AggregatedFlameProfileData {
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

    private fun processNextLayer(
        chartLayerLists: List<List<FlameProfileData>>,
        parent: AggregatedFlameProfileData
    ) {
        val traversed = chartLayerLists.flatten().groupBy { it.name }
        val aggregated = traversed.mapValues { (name, dataToAggregate) ->
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
