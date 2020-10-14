package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData

class StagesAnalyzer {

    fun analyze(
        stages: Stages,
        methodsAvailability: MethodsAvailability,
        methods: List<ProfileData>,
        shouldHideChild: Boolean
    ): List<WrongStage> {
        val result = mutableListOf<WrongStage>()
        stages.init()

        val addedMethods = mutableListOf<ProfileData>()

        for (method in methods) {
            stages.updateCurrentStage(method)

            if (!methodsAvailability.isMethodAvailable(method)) {
                continue
            }

            val methodStage = stages.getMethodsStage(method)
            if (stages.shouldMethodStageBeLaterThenCurrent(methodStage)) {
                if (!shouldHideChild || isMethodChildOfLastAdded(method, addedMethods)) {
                    result.add(WrongStage(method, stages.currentStage, methodStage))
                    if (shouldHideChild) {
                        addedMethods.add(method)
                    }
                }
            }
            if (methodStage == null) {
                // method without stage, maybe new
                if (!shouldHideChild || isMethodChildOfLastAdded(method, addedMethods)) {
                    result.add(WrongStage(method, stages.currentStage, null))
                    if (shouldHideChild) {
                        addedMethods.add(method)
                    }
                }
            }
        }
        return result
    }

    private fun isMethodChildOfLastAdded(
        method: ProfileData,
        addedMethods: List<ProfileData>
    ): Boolean {
        if (addedMethods.isEmpty()) {
            return false
        }

        val lastItem = addedMethods.last()
        var parent: ProfileData? = method.parent
        while (parent != null) {
            if (parent == lastItem) {
                return true
            }
            parent = parent.parent
        }
        return false
    }
}
