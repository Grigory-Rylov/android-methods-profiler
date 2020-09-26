package com.github.grishberg.profiler.analyzer

import com.github.grishberg.android.profiler.core.ProfileData
import java.awt.Color

data class ProfileDataImpl(
    override val name: String,
    override val level: Int,
    override val threadStartTimeInMillisecond: Double,
    override val globalStartTimeInMillisecond: Double,
    override var threadEndTimeInMillisecond: Double = 0.0,
    override var globalEndTimeInMillisecond: Double = 0.0,
    override var threadSelfTime: Double = 0.0,
    override var globalSelfTime: Double = 0.0,
    override var parent: ProfileDataImpl? = null,
    var color: Color? = null
): ProfileData {
    private val _children = mutableListOf<ProfileDataImpl>()
    override val children: List<ProfileDataImpl> = _children

    fun addChild(child: ProfileDataImpl) {
        _children.add(child)
        child.parent = this
    }
}
