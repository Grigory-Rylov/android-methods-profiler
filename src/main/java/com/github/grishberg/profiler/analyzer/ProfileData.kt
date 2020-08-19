package com.github.grishberg.profiler.analyzer

import java.awt.Color

data class ProfileData(
    val name: String,
    val level: Int,
    val threadStartTimeInMillisecond: Double,
    val globalStartTimeInMillisecond: Double,
    var threadEndTimeInMillisecond: Double = 0.0,
    var globalEndTimeInMillisecond: Double = 0.0,
    var threadSelfTime: Double = 0.0,
    var globalSelfTime: Double = 0.0,
    var parent: ProfileData? = null,
    var color: Color? = null
) {
    private val _children = mutableListOf<ProfileData>()
    val children: List<ProfileData> = _children

    fun addChild(child: ProfileData) {
        _children.add(child)
        child.parent = this
    }
}
