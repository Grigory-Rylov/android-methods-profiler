package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem

class StagesAnalyzer(
    private val stages: Stages
) {

    fun analyze(input: AnalyzerResult, thread: ThreadItem): List<WrongStage> {
        val result = mutableListOf<WrongStage>()
        val traceForThread = input.data[thread.threadId] ?: return emptyList()

        for (method in traceForThread) {
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
