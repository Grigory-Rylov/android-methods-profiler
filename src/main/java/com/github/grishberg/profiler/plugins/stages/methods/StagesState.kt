package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.profiler.plugins.stages.Stage

class StagesState(
    val stagesList: List<StageRelatedToMethods>
) {
    var currentStage: StageRelatedToMethods? = null
        private set

    private val stageWrappers = mutableListOf<StageWrapper>().apply {
        for (stage in stagesList) {
            add(StageWrapper(stage))
        }
    }

    private var nextStageIndex: Int = 0

    /**
     * Clear state.
     */
    fun initialState() {
        currentStage = null
        nextStageIndex = 0
    }

    /**
     * Sets current stage by given method.
     * returns true when stage switched
     */
    fun updateCurrentStage(methodName: String): Boolean {
        if (isLastStage()) {
            return false
        }
        var stageSwitched = false
        val current = stageWrappers[nextStageIndex]
        current.onMethodCalled(methodName)

        if (current.isStarted) {
            currentStage = current.stage
            nextStageIndex++
            stageSwitched = true
        }
        if (current.stage.methods.isEmpty()) {
            updateCurrentStage(methodName)
        }
        return stageSwitched
    }

    fun isLastStage() = nextStageIndex >= stagesList.size

    fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean {
        if (methodStage == null) {
            return false
        }
        val current = currentStage ?: return false
        val currentPosition = stagesList.indexOf(current)
        val methodStagePosition = stagesList.indexOf(methodStage)
        return methodStagePosition > currentPosition
    }

    private class StageWrapper(
        val stage: StageRelatedToMethods
    ) {
        var isStarted = false
            private set

        private var index: Int = 0

        private val methodsCount = IntArray(stage.methods.size) { 0 }

        /**
         * Process method [methodName] call and change state to started if needed.
         */
        fun onMethodCalled(methodName: String) {
            if (stage.methods.isEmpty()) {
                isStarted = true
            }
            if (isStarted) {
                return
            }

            if (methodName == stage.methods[index].name) {
                methodsCount[index]++
                if (methodsCount[index] > stage.methods[index].getMethodIndex()) {
                    index++
                }
            }
            isStarted = index >= stage.methods.size
        }
    }
}
