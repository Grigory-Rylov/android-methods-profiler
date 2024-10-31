package com.github.grishberg.profiler.plugins.stages.systrace

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.tracerecorder.SystraceRecord

class SystraceStagesState(
    private val recordsList: List<SystraceRecord>
) {
    private val stagesList = mutableListOf<StageFromSystrace>()
    private var currentStageIndex = 0
    var currentStage: StageFromSystrace? = null
        private set

    val stages: List<Stage>
        get() = ArrayList(stagesList)

    init {
        for (record in recordsList) {
            stagesList.add(StageFromSystrace(record))
        }
    }

    fun init() {
        currentStage = null
        currentStageIndex = 0
    }

    fun clone(): SystraceStagesState {
        return SystraceStagesState(recordsList)
    }

    fun updateCurrentStage(method: ProfileData) {
        if (stagesList.isEmpty()) {
            return
        }
        for (i in currentStageIndex until stagesList.size) {
            val stage = stagesList[i]
            val startTime = method.globalStartTimeInMillisecond
            val currentStageTime = stage.stage.startTime
            if (startTime < currentStageTime) {
                break
            }
            if (startTime >= currentStageTime &&
                (i == stagesList.size - 1 || startTime < stagesList[i + 1].stage.startTime)
            ) {
                currentStageIndex = i
                currentStage = stagesList[i]
                break
            }
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
