package com.github.grishberg.profiler.plugins.stages.systrace

import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.tracerecorder.SystraceRecord

class StageFromSystrace(
    private val stage: SystraceRecord
) : Stage {
    override val name: String
        get() = stage.name
}
