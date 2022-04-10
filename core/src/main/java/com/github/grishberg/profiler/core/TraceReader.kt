package com.github.grishberg.profiler.core

import java.io.File

interface TraceReader {
    fun readTraceFile(traceFile: File): AnalyzerResult
}
