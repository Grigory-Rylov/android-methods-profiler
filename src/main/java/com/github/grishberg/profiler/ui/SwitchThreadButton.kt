package com.github.grishberg.profiler.ui

import com.github.grishberg.android.profiler.core.ThreadItem
import javax.swing.JButton

private const val MAX_TEXT_WIDTH = 20
private const val EMPTY_TEXT = "<threads>"

class SwitchThreadButton : JButton() {
    var currentThread: ThreadItem? = null
        private set

    init {
        clear()
        toolTipText = "Open threads switcher"
    }

    fun switchThread(threadItem: ThreadItem) {
        currentThread = threadItem
        text = if (threadItem.name.length <= MAX_TEXT_WIDTH) {
            threadItem.name
        } else {
            "${threadItem.name.subSequence(0, MAX_TEXT_WIDTH)}…"
        }
    }

    fun clear() {
        text = EMPTY_TEXT
    }
}
