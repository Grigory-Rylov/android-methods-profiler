package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.profiler.core.ProfileData

interface MethodsAvailability {
    fun isMethodAvailable(method: ProfileData): Boolean
}
