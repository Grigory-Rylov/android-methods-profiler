package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.tracerecorder.common.RecorderLogger

class RecorderLoggerWrapper(
    private val log: AppLogger
) : RecorderLogger {
    override fun d(msg: String) {
        log.d(msg)
    }

    override fun e(msg: String) {
        log.e(msg)
    }

    override fun e(msg: String, t: Throwable) {
        log.e(msg, t)
    }

    override fun w(msg: String) {
        log.w(msg)
    }
}
