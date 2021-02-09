package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.tracerecorder.MethodTraceEventListener
import com.github.grishberg.tracerecorder.MethodTraceRecorder
import com.github.grishberg.tracerecorder.SerialNumber

interface MethodTraceRecorderFactory {
    fun create(
        listener: MethodTraceEventListener,
        systrace: Boolean,
        serialNumber: SerialNumber?
    ): MethodTraceRecorder
}

