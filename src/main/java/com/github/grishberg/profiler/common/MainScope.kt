package com.github.grishberg.profiler.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.swing.Swing
import kotlin.coroutines.CoroutineContext

class MainScope : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Swing

    fun destroy() = coroutineContext.cancelChildren()
}
