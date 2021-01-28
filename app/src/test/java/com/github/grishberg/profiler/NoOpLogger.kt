package com.github.grishberg.profiler

import com.github.grishberg.profiler.common.AppLogger

object NoOpLogger : AppLogger {
    override fun d(msg: String) = Unit
    override fun e(msg: String) = Unit

    override fun e(msg: String, t: Throwable) = Unit

    override fun w(msg: String) = Unit

    override fun i(msg: String) = Unit
}
