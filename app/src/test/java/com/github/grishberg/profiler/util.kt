package com.github.grishberg.profiler

import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.comparator.findAllOf

fun ProfileDataImpl.child(childName: String, start: Double, end: Double): ProfileDataImpl {
    val child = profileData(childName, start, end, this.level + 1)
    this.addChild(child)
    return child
}

fun profileData(name: String, start: Double, end: Double, level: Int = 0): ProfileDataImpl =
    ProfileDataImpl(
        name = name, level = level,
        threadStartTimeInMillisecond = start,
        globalStartTimeInMillisecond = start,
        threadEndTimeInMillisecond = end,
        globalEndTimeInMillisecond = end
    )

fun ProfileDataImpl.get(name: String) = children.findAllOf({ it.name == name}, { it })

fun List<ProfileDataImpl>.get(name: String) = map { it.get(name) }.flatten()

fun List<ProfileDataImpl>.countChildren(name: String) = sumOf {
    it.children.count { child ->
        child.name == name
    }
}

fun ProfileDataImpl.width() = globalEndTimeInMillisecond - globalStartTimeInMillisecond
