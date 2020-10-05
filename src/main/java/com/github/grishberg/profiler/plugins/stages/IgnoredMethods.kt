package com.github.grishberg.profiler.plugins.stages

/**
 * List of ignored package-prefixes.
 * Don't save this methods to stages configuration file.
 */
class IgnoredMethods {
    companion object {
        val exceptions = listOf(
            "java.",
            "android.",
            "androidx.",
            "dalvik.",
            "com.android.",
            "libcore.",
            "sun.",
            "kotlin.",
            "kotlinx.",
            "dagger.internal"
        )
    }
}
