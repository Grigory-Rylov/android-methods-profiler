package com.github.grishberg.profiler.common

import kotlinx.coroutines.CoroutineDispatcher

interface CoroutinesDispatchers {
    val worker: CoroutineDispatcher
    val ui: CoroutineDispatcher
}
