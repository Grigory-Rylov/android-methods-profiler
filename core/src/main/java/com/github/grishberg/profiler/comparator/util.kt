package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.comparator.model.ComparableProfileData

fun <K, V> List<Pair<K, List<V>>>.toMapMergeOnConflict(): Map<K, List<V>> {
    val result = mutableMapOf<K, MutableList<V>>()
    for ((key, value) in this) {
        result.getOrPut(key) { mutableListOf() }.addAll(value)
    }
    return result
}

fun <T, R> List<T>.findAllOf(comparator: (T) -> Boolean, of: (T) -> R): List<R> {
    val result = mutableListOf<R>()
    for (item in this) {
        if (comparator(item)) {
            result.add(of(item))
        }
    }
    return result
}

fun MutableList<ComparableProfileData>.sortedForCompare() = sortedWith(
    compareBy(
        { it.profileData.level },
        { it.profileData.globalStartTimeInMillisecond }
    )
)
