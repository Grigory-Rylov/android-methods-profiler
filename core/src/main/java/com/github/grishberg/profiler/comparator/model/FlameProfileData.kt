package com.github.grishberg.profiler.comparator.model

class FlameProfileData(
    val name: String,
    val count: Int,
    val left: Double,
    val top: Double,
    val width: Double
) {
    val children = mutableListOf<FlameProfileData>()

    fun addChild(data: FlameProfileData) = children.add(data)
}
