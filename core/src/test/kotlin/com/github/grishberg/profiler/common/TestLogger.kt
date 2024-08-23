package com.github.grishberg.profiler.common

class TestLogger: AppLogger {

    override fun d(msg: String) {
        println(msg)
    }

    override fun e(msg: String) {
        println(msg)
    }

    override fun e(msg: String, t: Throwable) {
        println(msg)
    }

    override fun w(msg: String) {
        println(msg)
    }

    override fun i(msg: String) {
        println(msg)
    }
}
