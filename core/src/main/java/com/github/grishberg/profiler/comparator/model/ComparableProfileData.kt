package com.github.grishberg.profiler.comparator.model

import com.github.grishberg.android.profiler.core.ProfileData

class ComparableProfileData(
    val id: CompareID,
    val profileData: ProfileData
) {
    var mark: MarkType = MarkType.NONE
        set(value) {
            if (!field.isOverridable()) {
                return
            }
            field = value
            if (!value.isOverridable()) {
                children.forEach { it.mark = value }
            }
        }
    val name get() = profileData.name
    val children = mutableListOf<ComparableProfileData>()

    fun addChild(child: ComparableProfileData) = children.add(child)
}

fun ProfileData.toComparable(
    parent: ComparableProfileData?,
    fillInto: MutableList<ComparableProfileData>
): ComparableProfileData {
    val root = ComparableProfileData(CompareID(name, parent?.id), this)
    fillInto.add(root)
    children.forEach {
        root.addChild(it.toComparable(root, fillInto))
    }
    return root
}
