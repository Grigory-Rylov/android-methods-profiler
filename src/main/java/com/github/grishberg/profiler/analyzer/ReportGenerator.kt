package com.github.grishberg.profiler.analyzer

import java.io.File

interface ReportGenerator {
    fun generate(file: File, onlyConstructor: Boolean, minimumDurationInMs: Int)
}
