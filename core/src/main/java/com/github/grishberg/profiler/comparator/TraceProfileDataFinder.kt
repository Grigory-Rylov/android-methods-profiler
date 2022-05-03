package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.android.profiler.core.ThreadItem

data class ProfileDataSearchInfo(
    val node: ProfileData,
    val ancestor: ProfileData?,
    val thread: ThreadItem
)

data class ProfileDataSearchResult(val node: ProfileData, val thread: ThreadItem)

class TraceProfileDataFinder(
    private val trace: AnalyzerResult
) {
    fun findToCompare(profileData: ProfileData): List<ProfileDataSearchResult> {
        var possibleData = trace.threads.map { thread ->
            trace.data[thread.threadId]!!.filter { data ->
                data.name == profileData.name && data.level == profileData.level &&
                        data.order() == profileData.order()
            }.map { data ->
                ProfileDataSearchInfo(data, data.parent, thread)
            }
        }.flatten()
        var parent = profileData.parent

        while (parent != null) {
            if (possibleData.size <= 1) {
                return possibleData.map { (node, _, thread) ->
                    ProfileDataSearchResult(node, thread)
                }
            }

            val parentSnapshot = parent
            possibleData = possibleData.filter { data ->
                data.ancestor?.name == parentSnapshot.name &&
                        data.ancestor.order() == parentSnapshot.order()
            }.map { (node, ancestor, thread) ->
                ProfileDataSearchInfo(node, ancestor?.parent, thread)
            }
            parent = parent.parent
        }

        return possibleData.map { (node, _, thread) ->
            ProfileDataSearchResult(node, thread)
        }
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
