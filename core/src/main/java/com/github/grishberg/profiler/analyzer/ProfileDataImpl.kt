package com.github.grishberg.profiler.analyzer

import com.github.grishberg.android.profiler.core.ProfileData

data class ProfileDataImpl(
    override val name: String,
    override val level: Int,
    override val threadStartTimeInMillisecond: Double,
    override val globalStartTimeInMillisecond: Double,
    override var threadEndTimeInMillisecond: Double = -1.0,
    override var globalEndTimeInMillisecond: Double = -1.0,
    override var threadSelfTime: Double = 0.0,
    override var globalSelfTime: Double = 0.0,
    override var parent: ProfileDataImpl? = null
) : ProfileData {
    private val _children = mutableListOf<ProfileDataImpl>()
    override val children: List<ProfileDataImpl> = _children

    fun addChild(child: ProfileDataImpl) {
        _children.add(child)
        child.parent = this
    }

    override fun toString(): String {
        return name
    }

    fun updateSelfTime() {
        if (threadSelfTime > 0.0 && globalSelfTime > 0.0) {
            return
        }
        threadSelfTime = threadEndTimeInMillisecond - threadStartTimeInMillisecond
        globalSelfTime = globalEndTimeInMillisecond - globalStartTimeInMillisecond
        for (child in children) {
            threadSelfTime -= child.threadEndTimeInMillisecond - child.threadStartTimeInMillisecond
            globalSelfTime -= child.globalEndTimeInMillisecond - child.globalStartTimeInMillisecond

            child.updateSelfTime()
        }
    }
}
