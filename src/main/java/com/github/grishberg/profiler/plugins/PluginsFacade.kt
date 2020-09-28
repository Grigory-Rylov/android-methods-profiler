package com.github.grishberg.profiler.plugins

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.android.profiler.plugins.CallTraceAnalyzer
import com.github.grishberg.android.profiler.plugins.CallTraceAnalyzerResult
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import com.github.grishberg.profiler.ui.dialogs.plugins.PluginAnalyzerDialog
import org.pf4j.JarPluginManager
import java.awt.Frame
import java.io.File
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.SwingUtilities

private const val PLUGINS_DIT = "plugins"

class PluginsFacade(
    private val owner: Frame,
    private val focusElementDelegate: FocusElementDelegate,
    settings: SettingsRepository
) {
    private val pluginsDir = File(settings.filesDir(), PLUGINS_DIT)
    private val pluginManager = JarPluginManager(pluginsDir.toPath())
    var currentTraceProfiler: AnalyzerResult? = null
    var currentThread: ThreadItem? = null

    init {
        pluginManager.loadPlugins()
        pluginManager.startPlugins()
    }

    /**
     * Returns plugins menu or null if there is no any plugins
     */
    fun createPluginsMenu(menuBar: JMenuBar) {
        val callTraceAnalyzers: List<CallTraceAnalyzer> = pluginManager.getExtensions(CallTraceAnalyzer::class.java)
        if (callTraceAnalyzers.isEmpty()) {
            return
        }

        val pluginsMenu = JMenu("Plugins")
        for (analyzer in callTraceAnalyzers) {
            val analyzerMenuItem = JMenuItem(analyzer.analyzerName())
            pluginsMenu.add(analyzerMenuItem)
            analyzerMenuItem.addActionListener {
                currentTraceProfiler?.let {
                    launchPlugin(analyzer, it)
                }
            }
        }
        menuBar.add(pluginsMenu)
    }

    private fun launchPlugin(analyzer: CallTraceAnalyzer, trace: AnalyzerResult) {
        val dialog = PluginAnalyzerDialog(owner, focusElementDelegate, analyzer.analyzerName())
        dialog.isVisible = true
        //TODO: request current thread id.
        dialog.showProgress()

        analyzer.analyzeCallTrace(trace, currentThread, object : CallTraceAnalyzer.Callback {
            override fun onResultReady(result: CallTraceAnalyzerResult) {
                SwingUtilities.invokeLater {
                    dialog.showResult(result)
                }
            }
        })

    }

    fun destory() {
        pluginManager.unloadPlugins()
    }
}
