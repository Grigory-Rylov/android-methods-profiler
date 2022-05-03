package com.github.grishberg.profiler.comparator.model

enum class MarkType {
    NONE, COMPARED, OLD, NEW, CHANGE_ORDER;

    fun isOverridable(): Boolean {
        return this == NONE
    }

    fun isVisited(): Boolean {
        return this != NONE
    }

    fun isComparable(): Boolean {
        return this != OLD && this != NEW && this != COMPARED
    }

    fun isOverrideChildren(): Boolean {
        return this == OLD || this == NEW
    }
}
