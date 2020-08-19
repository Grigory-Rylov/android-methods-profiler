package com.github.grishberg.profiler.analyzer

class ThreadItem(val name: String, val threadId: Int) {
    override fun toString() = name
}