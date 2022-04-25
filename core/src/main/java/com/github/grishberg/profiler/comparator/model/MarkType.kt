package com.github.grishberg.profiler.comparator.model

enum class MarkType {
    NONE, COMPARED, OLD, NEW, CHANGE_ORDER, SUSPICIOUS;

    fun isOverridable(): Boolean {
        return this == NONE || this == SUSPICIOUS || this == COMPARED
    }

    fun isVisited(): Boolean {
        return this != NONE && this != SUSPICIOUS
    }
}
