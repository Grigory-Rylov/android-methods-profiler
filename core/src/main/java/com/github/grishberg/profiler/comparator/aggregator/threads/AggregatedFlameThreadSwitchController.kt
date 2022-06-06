package com.github.grishberg.profiler.comparator.aggregator.threads

interface AggregatedFlameThreadSwitchController {

    fun getThreads(): List<FlameThreadItem>

    fun onThreadSelected(thread: FlameThreadItem)
}
