package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData

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
                result.add(WrongStage(method, stages.currentStage, methodStage))
            }
            if (methodStage == null) {
                // method without stage, maybe new
                result.add(WrongStage(method, stages.currentStage, null))
            }
        }
        return result
    }
}
