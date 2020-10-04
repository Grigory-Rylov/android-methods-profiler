package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.android.profiler.core.ThreadItem

class StagesAnalyzer(
    private val stages: Stages
) {

    fun analyze(methods: List<ProfileData>): List<WrongStage> {
        val result = mutableListOf<WrongStage>()
        stages.init()

        for (method in methods) {
            stages.updateCurrentStage(method)

            if (!stages.isMethodAvailable(method)) {
                continue
            }

            val methodStage = stages.getMethodsStage(method)
            if (stages.shouldMethodStageBeLaterThenCurrent(methodStage)) {
                if (methodStage != null) {
                    result.add(WrongStage(method, stages.currentStage, methodStage))
                } else {
                    result.add(WrongStage(method, stages.currentStage, methodStage))
                }
            }
        }
        return result
    }
}
