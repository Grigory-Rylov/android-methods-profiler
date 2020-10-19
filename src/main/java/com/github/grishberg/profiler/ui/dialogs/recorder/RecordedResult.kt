package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.tracerecorder.SystraceRecord
import java.io.File

data class RecordedResult(
    val recorderTraceFile: File,
    val systraces: List<SystraceRecord>
)
