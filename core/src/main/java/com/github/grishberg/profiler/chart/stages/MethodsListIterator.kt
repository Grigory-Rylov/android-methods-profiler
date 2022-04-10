package com.github.grishberg.profiler.chart.stages

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.ProfileRectangle

class MethodsListIterator(
    methodsList: List<ProfileRectangle>
) : Iterator<ProfileData> {
    private val innerIterator = methodsList.iterator()

    override fun hasNext(): Boolean = innerIterator.hasNext()

    override fun next(): ProfileData = innerIterator.next().profileData
}
