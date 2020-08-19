package com.github.grishberg.profiler.ui

interface ShowDialogDelegate {
    fun showOpenFileChooser(inNewWindow: Boolean = false)
    fun showNewTraceDialog(inNewWindow: Boolean = false)
    fun showReportsDialog()
    fun showScaleRangeDialog()
}
