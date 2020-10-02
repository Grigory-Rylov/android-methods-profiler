package com.github.grishberg.profiler.plugins.stages

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
class Stage(
    val name: String,
    val methods: List<MethodWithIndex>
) {
    @Transient
    var isStarted = false
        private set

    @Transient
    private var index: Int = 0

    @Transient
    private val methodsCount = IntArray(methods.size) { 0 }

    /**
     * Process method [methodName] call and change state to started if needed.
     */
    fun onMethodCalled(methodName: String) {
        if (methods.isEmpty()) {
            isStarted = true
        }
        if (isStarted) {
            return
        }

        if (methodName == methods[index].name) {
            methodsCount[index]++
            if (methodsCount[index] > methods[index].getMethodIndex()) {
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
