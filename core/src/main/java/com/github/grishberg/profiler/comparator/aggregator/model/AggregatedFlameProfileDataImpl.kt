package com.github.grishberg.profiler.comparator.aggregator.model

class AggregatedFlameProfileDataImpl(
    override val name: String,
    val sumCountAggregated: Int,
    val sumWidthAggregated: Double,
    val countAggregated: Int,
    var left: Double,
    val top: Double,
    var width: Double = 0.0
): AggregatedFlameProfileData {
    override val mean: Double get() = sumCountAggregated.toDouble() / countAggregated
    val meanWidth: Double get() = sumWidthAggregated / countAggregated
    override val children = mutableListOf<AggregatedFlameProfileDataImpl>()
    override var mark: FlameMarkType = FlameMarkType.NONE
        set(value) {
            field = value
            if (value.isOverrideChildren()) {
                children.forEach { it.mark = value }
            }
        }

    fun addChild(data: AggregatedFlameProfileDataImpl) = children.add(data)
}
