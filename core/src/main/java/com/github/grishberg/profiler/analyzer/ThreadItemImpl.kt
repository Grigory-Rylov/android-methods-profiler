package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.core.ThreadItem

class ThreadItemImpl(
    override val name: String,
    override val threadId: Int
): ThreadItem {
    override fun toString() = name
}
