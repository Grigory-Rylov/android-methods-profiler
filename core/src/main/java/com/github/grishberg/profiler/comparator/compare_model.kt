package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import java.util.DoubleSummaryStatistics

enum class MarkType {
    NONE, COMPARED, OLD, NEW, CHANGE_ORDER, SUSPICIOUS;

    fun isOverridable(): Boolean {
        return this == NONE || this == SUSPICIOUS || this == COMPARED
    }

    fun isVisited(): Boolean {
        return this != NONE && this != SUSPICIOUS
    }
}

enum class FlameMarkType {
    NONE, NEW_NEW, OLD_OLD, MAYBE_NEW, MAYBE_OLD;

    fun isOverrideChildren(): Boolean {
        return this == NEW_NEW || this == OLD_OLD
    }
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

class ComparableFlameProfileData(
    val id: CompareID,
    val count: Int,
    val left: Double,
    val top: Double,
    val width: Double
) {
    val children = mutableListOf<ComparableFlameProfileData>()
    var mark: FlameMarkType = FlameMarkType.NONE
        set(value) {
            field = value
            if (value.isOverrideChildren()) {
                children.forEach { it.mark = value }
            }
        }

    fun addChild(data: ComparableFlameProfileData) = children.add(data)
}

data class ComparableFlameChildHolder(
    var minLeft: Double,
    val children: MutableList<ProfileData> = mutableListOf()
) {
    val count get() = children.size
}
