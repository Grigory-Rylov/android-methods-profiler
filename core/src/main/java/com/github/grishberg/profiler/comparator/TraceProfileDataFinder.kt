package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData

data class ProfileDataSearchInfo(val node: ProfileData, val ancestor: ProfileData?)

class TraceProfileDataFinder(
    private val trace: List<ProfileData>
) {
    fun findToCompare(profileData: ProfileData): List<ProfileData> {
        var possibleData = trace.filter { data ->
            data.name == profileData.name && data.level == profileData.level &&
                    data.order() == profileData.order()
        }.map { node ->
            ProfileDataSearchInfo(node, node.parent)
        }
        var parent = profileData.parent

        while (parent != null) {
            val parentSnapshot = parent
            possibleData = possibleData.filter { data ->
                data.ancestor?.name == parentSnapshot.name &&
                        data.ancestor.order() == parentSnapshot.order()
            }.map { (node, ancestor) ->
                ProfileDataSearchInfo(node, ancestor?.parent)
            }
            parent = parent.parent
        }

        return possibleData.map { it.node }
    }

    private fun ProfileData?.order(): Int {
        if (this == null) return -1
        var order = 1
        val children = parent?.children ?: return order
        for (child in children) {
            if (child === this) {
                return order
            }
            if (child.name == name) {
                order++
            }
        }
        return order
    }
}
