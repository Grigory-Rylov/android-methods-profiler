package com.github.grishberg.profiler.chart

interface FoundNavigationListener<T> {
    fun onSelected(count: Int, selectedIndex: Int, selectedElement: T)
    fun onNavigatedOverLastItem()
    fun onNavigatedOverFirstItem()
}
