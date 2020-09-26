package com.github.grishberg.profiler

import com.github.grishberg.profiler.analyzer.ProfileDataImpl


interface ProfileCollection {
    fun setupRange(minTime: Double, maxTime: Double, minLevel: Int, maxLevel: Int)
    fun addValue(newRecord: ProfileDataImpl)
    fun render()
}
