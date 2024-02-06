package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.core.ProfileData
import java.io.File

interface ReportGenerator {
    fun generate(
        file: File,
        onlyConstructor: Boolean,
        minimumDurationInMs: Int,
        packageFilter: String
    )
}
