package com.github.grishberg.profiler.plugins.stages.systrace

import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.tracerecorder.SystraceRecord

class StageFromSystrace(
    val stage: SystraceRecord
) : Stage {
    override val name: String
        get() = stage.name

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (other is StageFromSystrace) {
            return other.name == name && other.stage.startTime == stage.startTime
        }
        return false
    }
}
