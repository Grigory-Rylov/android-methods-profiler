package com.github.grishberg.profiler.chart

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.ui.Main
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * Methods right click popup menu.
 */
class MethodsPopupMenu(
    private val main: Main,
    private val chart: ProfilerPanel,
    private val selectedMethod: ProfileData
) : JPopupMenu() {
    private val flameChartMenuItem = JMenuItem("Flame Chart")
    private val copyNameMenuItem = JMenuItem("Copy name")
    private val copyStackTraceMenuItem = JMenuItem("Copy stacktrace")

    init {
        add(copyNameMenuItem)
        add(copyStackTraceMenuItem)
        add(flameChartMenuItem)

        flameChartMenuItem.addActionListener {
            main.showFlameChartDialog()
        }
        copyNameMenuItem.addActionListener {
            val stringSelection = StringSelection(selectedMethod.name)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }

        copyStackTraceMenuItem.addActionListener {
            val stackTrace: String? = chart.copySelectedStacktrace()
            if (stackTrace != null) {
                val stringSelection = StringSelection(stackTrace)
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(stringSelection, null)
            }
        }
    }
}
