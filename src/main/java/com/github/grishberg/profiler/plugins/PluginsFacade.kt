package com.github.grishberg.profiler.plugins

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import javax.swing.JFrame
import javax.swing.JMenuBar

class PluginsFacade(
    frame: JFrame,
    focusElementDelegate: FocusElementDelegate,
    settings: SettingsRepository
) {
    var currentThread: ThreadItem? = null
    var currentTraceProfiler: AnalyzerResult? = null

    fun createPluginsMenu(menuBar: JMenuBar) = Unit
}
