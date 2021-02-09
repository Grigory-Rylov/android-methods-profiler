package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.android.adb.AdbLogger
import com.github.grishberg.profiler.common.AppLogger

class RecorderLoggerWrapper(
    private val log: AppLogger
) : AdbLogger {
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
