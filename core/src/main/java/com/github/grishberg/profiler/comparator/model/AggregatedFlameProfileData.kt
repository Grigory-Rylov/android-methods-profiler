package com.github.grishberg.profiler.comparator.model

interface AggregatedFlameProfileData {
    val name: String

    val mean: Double

    var mark: FlameMarkType

    val children: List<AggregatedFlameProfileData>
}
