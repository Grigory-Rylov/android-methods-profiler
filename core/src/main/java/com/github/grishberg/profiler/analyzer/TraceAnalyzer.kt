package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.analyzer.converter.LegacyTraceAnalyzerTraceAnalyzer
import com.github.grishberg.profiler.analyzer.converter.NameConverter
import com.github.grishberg.profiler.analyzer.converter.NoOpNameConverter
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.core.AnalyzerResult
import java.io.File

class TraceAnalyzer(
    private val log: AppLogger
) {
    var nameConverter: NameConverter = NoOpNameConverter

    fun analyze(traceFile: File): AnalyzerResult {
        return try {
            SimplePerfAnalyzer(log, nameConverter).analyze(traceFile)
        } catch (e: WrongFormatException){
            LegacyTraceAnalyzerTraceAnalyzer(log, nameConverter).analyze(traceFile)
        }
    }
}

