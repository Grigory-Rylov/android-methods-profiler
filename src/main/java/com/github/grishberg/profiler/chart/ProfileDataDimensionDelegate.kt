package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.analyzer.ProfileDataImpl

interface ProfileDataDimensionDelegate {
    fun calculateTopForLevel(level: Int): Double
    fun calculateEndXForTime(record: ProfileDataImpl): Double
    fun calculateStartXForTime(record: ProfileDataImpl): Double
    fun levelHeight(): Double
    fun fontTopOffset(): Int
}