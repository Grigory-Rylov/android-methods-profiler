package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData

/**
 * @param name - method name
 * @param index - index of called method [name]
 */
data class MethodWithIndex(val name: String, val index: Int = 0)

class Stage(
    val name: String,
    val methods: List<MethodWithIndex>
) {
    var isStarted = false
        private set

    private var index: Int = 0
    private val methodsCount = IntArray(methods.size) { 0 }

    fun onMethodCalled(profileData: ProfileData) {
        if (methods.isEmpty()) {
            isStarted = true
        }
        if (isStarted) {
            return
        }

        if (profileData.name == methods[index].name) {
            methodsCount[index]++
            if (methodsCount[index] > methods[index].index) {
                index++
            }
        }
        isStarted = index >= methods.size
    }

    override fun equals(other: Any?): Boolean {
        if (other is Stage) {
            return name == other.name
        }
        return false
    }

    override fun toString(): String {
        return name
    }
}
