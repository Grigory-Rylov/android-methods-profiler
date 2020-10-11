package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData

interface Stages {
    val currentStage: Stage?

    fun init()

    /**
     * Sets current stage by given method.
     */
    fun updateCurrentStage(method: ProfileData)

    fun getMethodsStage(method: ProfileData): Stage?

    fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean
}
