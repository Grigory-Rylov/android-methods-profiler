package com.github.grishberg.profiler.ui

interface ShowDialogDelegate {
    fun showOpenFileChooser(inNewWindow: Boolean = false)
    fun showNewTraceDialog(inNewWindow: Boolean = false)
    fun showCurrentThreadMethodsReportsDialog()
    fun showAllThreadReportsDialog()
    fun showScaleRangeDialog()
}
