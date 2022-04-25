package com.github.grishberg.profiler.comparator.model

class AggregatedFlameProfileData(
    val name: String,
    val sumCountAggregated: Int,
    val sumWidthAggregated: Double,
    val countAggregated: Int,
    var left: Double,
    val top: Double,
    var width: Double = 0.0
) {
    val mean: Double get() = sumCountAggregated.toDouble() / countAggregated
    val meanWidth: Double get() = sumWidthAggregated / countAggregated
    val children = mutableListOf<AggregatedFlameProfileData>()
    var mark: FlameMarkType = FlameMarkType.NONE
        set(value) {
            field = value
            if (value.isOverrideChildren()) {
                children.forEach { it.mark = value }
            }
        }

    fun addChild(data: AggregatedFlameProfileData) = children.add(data)
}
