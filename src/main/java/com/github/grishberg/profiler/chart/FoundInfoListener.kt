package com.github.grishberg.profiler.chart

interface FoundInfoListener {
    fun onFound(count: Int, selectedIndex: Int)
    fun onNotFound(text: String, ignoreCase: Boolean)
}
