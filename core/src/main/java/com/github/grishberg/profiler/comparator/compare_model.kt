package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData

enum class MarkType {
    NONE, COMPARED, OLD, NEW, CHANGE_ORDER, SUSPICIOUS;

    fun isOverridable(): Boolean {
        return this == NONE || this == SUSPICIOUS || this == COMPARED
    }

    fun isVisited(): Boolean {
        return this != NONE && this != SUSPICIOUS
    }
}

fun ProfileData.toComparable(
    parent: ComparableProfileData?,
    fillInto: MutableList<ComparableProfileData>
): ComparableProfileData {
    val root = ComparableProfileData(CompareID(name, parent?.id), parent, this)
    fillInto.add(root)
    children.forEach {
        root.addChild(it.toComparable(root, fillInto))
    }
    return root
}

class ComparableProfileData(
    val id: CompareID,
    val parent: ComparableProfileData?,
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

data class CompareID(
    val name: String,
    val parent: CompareID?
) {
    private var _hashcode = Int.MIN_VALUE

    override fun hashCode(): Int {
        if (_hashcode != Int.MIN_VALUE) return _hashcode

        _hashcode = name.hashCode()
        _hashcode = 31 * _hashcode + (parent?.hashCode() ?: 0)
        return _hashcode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompareID

        if (name != other.name) return false
        if (hashCode() != other.hashCode()) return false

        return true
    }
}
