package com.github.grishberg.profiler

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.comparator.findAllOf
import com.github.grishberg.profiler.comparator.aggregator.model.AggregatedFlameProfileData
import com.github.grishberg.profiler.comparator.aggregator.model.FlameMarkType

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

data class AggregatedTestFlameProfileData(
    override val name: String,
    override val mean: Double,
    override var mark: FlameMarkType
) : AggregatedFlameProfileData {
    override val children = mutableListOf<AggregatedFlameProfileData>()

    fun addChild(data: AggregatedFlameProfileData) = children.add(data)
}

fun aggregatedData(name: String, mean: Double = 1.0): AggregatedTestFlameProfileData {
    return AggregatedTestFlameProfileData(
        name = name,
        mean = mean,
        mark = FlameMarkType.NONE
    )
}

fun AggregatedTestFlameProfileData.child(
    name: String,
    mean: Double
): AggregatedTestFlameProfileData {
    val child = aggregatedData(name = name, mean = mean)
    this.addChild(child)
    return child
}

fun MutableList<ProfileData>.fillWith(root: ProfileData) {
    this.add(root)
    for (child in root.children) {
        this.fillWith(child)
    }
}
