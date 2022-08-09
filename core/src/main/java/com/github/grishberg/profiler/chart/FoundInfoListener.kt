package com.github.grishberg.profiler.chart

@Deprecated("Use FoundNavigationListener")
interface FoundInfoListener<T> {
    fun onFound(count: Int, selectedIndex: Int, selectedElement: T)
    fun onNotFound(text: String, ignoreCase: Boolean)
}
