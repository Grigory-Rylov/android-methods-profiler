package com.github.grishberg.profiler.comparator.model

enum class FlameMarkType {
    NONE, NEW_NEW, OLD_OLD, MAYBE_NEW, MAYBE_OLD;

    fun isOverrideChildren(): Boolean {
        return this == NEW_NEW || this == OLD_OLD
    }
}
