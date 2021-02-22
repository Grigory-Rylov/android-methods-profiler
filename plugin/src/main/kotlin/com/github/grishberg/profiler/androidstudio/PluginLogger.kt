package com.github.grishberg.profiler.androidstudio

import com.github.grishberg.profiler.common.AppLogger
import com.intellij.openapi.diagnostic.Logger

class PluginLogger : AppLogger {
    private val log: Logger = Logger.getInstance("YAMP")
    override fun d(msg: String) {
        log.debug(msg)
    }

    override fun e(msg: String) {
        log.error(msg)
    }

    override fun e(msg: String, t: Throwable) {
        log.error(msg, t)
    }

    override fun w(msg: String) {
        log.warn(msg)
    }

    override fun i(msg: String) {
        log.info(msg)
    }
}
