package com.github.grishberg.profiler.chart

interface FoundInfoListener<T> {
    fun onFound(count: Int, selectedIndex: Int, selectedElement: T)
    fun onNotFound(text: String, ignoreCase: Boolean)
}
