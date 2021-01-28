package com.github.grishberg.profiler.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SimpleConsoleLogger(
    appFilesDir: String
) : AppLogger {

    private val log: Logger

    init {
        System.setProperty("androidTraceViewerLogDir", appFilesDir)
        log = LoggerFactory.getLogger(SimpleConsoleLogger::class.java)
    }

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