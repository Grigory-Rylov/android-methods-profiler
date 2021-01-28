package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.profiler.plugins.stages.Stage

/**
 * @param name - method name
 * @param index - index of called method [name]
 */
data class MethodWithIndex(
    val name: String,
    val index: Int? = null
) {
    fun getMethodIndex(): Int {
        return index ?: 0
    }
}

/**
 * Represents Application running stage.
 * Starts after [methods].
 */
class StageRelatedToMethods(
    override val name: String,
    val methods: List<MethodWithIndex>,
    val color: String? = null
) : Stage {
    override fun equals(other: Any?): Boolean {
        if (other is StageRelatedToMethods) {
            return name == other.name
        }
        return false
    }

    override fun toString(): String {
        return name
    }
}
