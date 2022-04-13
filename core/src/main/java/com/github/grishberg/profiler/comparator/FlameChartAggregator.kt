package com.github.grishberg.profiler.comparator

import kotlin.math.min

private const val INCLUDE_METHOD_THRESHOLD = 0.4

class FlameChartAggregator {

    fun aggregate(charts: List<FlameProfileData>): AggregatedFlameProfileData {
        check(charts.all { it.name == "INIT" })
        val left = charts.minOf { it.left }
        val width = charts.maxOf { it.width }

        val result = AggregatedFlameProfileData(
            name = "INIT",
            sumCountAggregated = -1,
            countAggregated = -1,
            left,
            Double.MIN_VALUE,
            width
        )

        processNextLayer(charts.map { it.children }.flatten(), result)

        return result
    }

    private fun processNextLayer(chartLayerLists: List<FlameProfileData>, parent: AggregatedFlameProfileData) {
        val traversed: Map<String, List<FlameProfileData>> = chartLayerLists.groupBy { it.name }
        val aggregated: Map<String, AggregatedFlameProfileData> = traversed.mapValues { (name, dataToAggregate) ->
            val acc = AggregatedFlameProfileData(
                name,
                sumCountAggregated = 0,
                countAggregated = 0,
                left = Double.MAX_VALUE,
                dataToAggregate.first().top,
                width = 0.0  // will be set later
            )
            dataToAggregate.fold(acc) { current, next ->
                AggregatedFlameProfileData(
                    name,
                    current.sumCountAggregated + next.count,
                    current.countAggregated + 1,
                    left = min(current.left, next.left),
                    top = current.top,
                    width = 0.0  // will be set later
                )
            }
        }

        val parentMethodCallCount = aggregated.values.sumByDouble { data ->
            if (data.countAggregated.toDouble() / chartLayerLists.size > INCLUDE_METHOD_THRESHOLD) {
                data.mean
            } else 0.0
        }

        val sorted = aggregated.toList().sortedBy { (_, data) -> data.left }.toMap().toMutableMap()

        var curLeft = parent.left
        for ((name, data) in sorted) {
            if (data.countAggregated.toDouble() / chartLayerLists.size <= INCLUDE_METHOD_THRESHOLD) continue
            val width = data.mean * parent.width / parentMethodCallCount
            data.left = curLeft
            data.width = width
            parent.addChild(data)
            processNextLayer(traversed[name]!!, data)
            curLeft += width
        }
    }
}
