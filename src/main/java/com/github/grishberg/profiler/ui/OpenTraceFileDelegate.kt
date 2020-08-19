package com.github.grishberg.profiler.ui

import java.io.File

interface OpenTraceFileDelegate {
    fun openTraceFile(traceFile: File)
}