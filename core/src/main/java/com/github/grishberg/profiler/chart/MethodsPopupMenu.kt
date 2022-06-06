package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.common.createAccelerator
import com.github.grishberg.profiler.common.createControlAccelerator
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.dialogs.ElementWithColorDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * Methods right click popup menu.
 */
class MethodsPopupMenu(
    private val main: Main,
    private val frame: Frame,
    private val chart: CallTracePanel,
    private val selectedMethod: ProfileData,
    private val stagesFacade: StagesFacade
) : JPopupMenu() {
    private val flameChartMenuItem = JMenuItem("Flame Chart")
    private val copyNameMenuItem = JMenuItem("Copy name").apply {
        accelerator = createControlAccelerator('C')
    }
    private val copyStackTraceMenuItem = JMenuItem("Copy stacktrace").apply {
        accelerator = createControlAccelerator('S')
    }
    private val addStageMenuItem = JMenuItem("Add new stage")
    private val removeStageMenuItem = JMenuItem("Remove stage")
    private val addBookmark = JMenuItem("Add bookmark").apply {
        accelerator = createAccelerator('M')
    }
    private val compareTracesItem = JMenuItem("Compare")
    private val compareFlameChartItem = JMenuItem("Compare flame chart")

    init {
        add(copyNameMenuItem)
        add(copyStackTraceMenuItem)
        add(addBookmark)
        add(flameChartMenuItem)

        addBookmark.addActionListener {
            main.addBookmark()
        }

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

        val stageForMethod = stagesFacade.stageForMethod(selectedMethod)
        if (stageForMethod != null) {
            add(removeStageMenuItem)
            removeStageMenuItem.addActionListener {
                stagesFacade.removeStage(stageForMethod)
            }
        } else {
            add(addStageMenuItem)
            addStageMenuItem.addActionListener {
                val dialog = ElementWithColorDialog(frame, "Create new stage", true)
                dialog.showDialog(chart)
                val result = dialog.result
                if (result != null) {
                    stagesFacade.createStage(selectedMethod, result.title, result.color)
                }
            }
        }

        if (main.isCompareMenuItemEnabled) {
            add(compareTracesItem)
            add(compareFlameChartItem)
        }
        compareTracesItem.addActionListener {
            main.onCompareMenuItemClicked()
        }
        compareFlameChartItem.addActionListener{
            main.onCompareFlameChartMenuItemClicked()
        }
    }
}
