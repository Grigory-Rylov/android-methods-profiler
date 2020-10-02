package com.github.grishberg.profiler.plugins.stages

class StagesState(
    private val stagesList: List<Stage>
) {
    var currentStage: Stage? = null
        private set

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
     */
    fun updateCurrentStage(methodName: String) {
        if (nextStageIndex >= stagesList.size) {
            return
        }
        val current = stagesList[nextStageIndex]
        current.onMethodCalled(methodName)

        if (current.isStarted) {
            currentStage = current
            nextStageIndex++
        }
        if (current.methods.isEmpty()) {
            updateCurrentStage(methodName)
        }
    }

    fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean {
        if (methodStage == null) {
            return false
        }
        val current = currentStage ?: return false
        val currentPosition = stagesList.indexOf(current)
        val methodStagePosition = stagesList.indexOf(methodStage)
        return methodStagePosition > currentPosition
    }
}
