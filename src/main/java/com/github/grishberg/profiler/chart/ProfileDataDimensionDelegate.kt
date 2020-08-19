package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.analyzer.ProfileData

interface ProfileDataDimensionDelegate {
    fun calculateTopForLevel(level: Int): Double
    fun calculateEndXForTime(record: ProfileData): Double
    fun calculateStartXForTime(record: ProfileData): Double
    fun levelHeight(): Double
    fun fontTopOffset(): Int
}