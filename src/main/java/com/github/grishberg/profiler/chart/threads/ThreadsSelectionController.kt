package com.github.grishberg.profiler.chart.threads

import com.github.grishberg.android.profiler.core.ThreadItem

interface ThreadsSelectionView {
    fun showThreads(threads: List<ThreadItem>)
    fun hide()
}

class ThreadsSelectionController() {
    var view: ThreadsSelectionView? = null

    fun showThreads(threads: List<ThreadItem>) {
        view?.showThreads(threads)
    }

    fun onThreadSelected(threadId: Int) {
        view?.hide()
    }
}
