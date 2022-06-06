package com.github.grishberg.profiler.comparator.aggregator.threads

import com.github.grishberg.profiler.core.ThreadItem

data class FlameThreadItem(override val name: String) : ThreadItem {
    override val threadId: Int = -1

    companion object {
        val MAIN = FlameThreadItem("main")
        val ALL_BG_THREADS = FlameThreadItem("all bg threads")
    }
}
