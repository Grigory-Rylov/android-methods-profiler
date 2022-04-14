package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.profiler.core.ProfileData

data class WrongStage(
    val method: ProfileData,
    val currentStage: Stage?,
    val correctStage: Stage?
) {
    override fun toString(): String {
        return method.name
    }
}
