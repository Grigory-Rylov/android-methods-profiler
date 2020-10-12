package com.github.grishberg.profiler.plugins.stages.systrace

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.Stages
import com.github.grishberg.tracerecorder.SystraceRecord
import java.io.File

class StagesFromSystrace(
    private val recordsList: List<SystraceRecord>,
    private val methodStages: Map<String, Stage?>,
) : Stages {
    override var currentStage: Stage? = null
    private var currentStageIndex = 0
    private val stagesList = mutableListOf<StageFromSystrace>()

    override fun init() {
        stagesList.clear()
        for (record in recordsList) {
            stagesList.add(StageFromSystrace(record))
        }
    }

    override fun updateCurrentStage(method: ProfileData) {
        for (i in currentStageIndex downTo recordsList.size) {
            val stage = stagesList[i]
            val startTime = method.globalStartTimeInMillisecond
            val currentStageTime = stage.stage.startTime * 1000
            if (startTime >= currentStageTime &&
                (i >= stagesList.size || startTime < stagesList[i].stage.startTime * 100)
            ) {
                currentStageIndex = i
                currentStage = stage
                return
            }
        }
    }

    override fun getMethodsStage(method: ProfileData): Stage? = methodStages[method.name]

    override fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean {
        if (methodStage == null) {
            return false
        }
        val current = currentStage ?: return false
        val currentPosition = stagesList.indexOf(current)
        val methodStagePosition = stagesList.indexOf(methodStage)
        return methodStagePosition > currentPosition
    }

    override fun saveToFile(file: File, input: List<ProfileData>) {
        // TODO: implement saving to file
    }
}
