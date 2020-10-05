package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData

class MethodsListIterator(
    methodsList: List<ProfileData>
) : Iterator<ProfileData> {
    private val innerIterator = methodsList.iterator()

    override fun hasNext(): Boolean = innerIterator.hasNext()

    override fun next(): ProfileData = innerIterator.next()
}
