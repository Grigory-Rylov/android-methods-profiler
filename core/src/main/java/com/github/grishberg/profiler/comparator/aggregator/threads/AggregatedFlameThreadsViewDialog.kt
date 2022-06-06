package com.github.grishberg.profiler.comparator.aggregator.threads

import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.Color
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.math.max

private const val THREAD_NAME_WIDTH = 360
private const val THREAD_NAME_HEIGHT = 32
private const val SELECT_ACTION = "Select"

class AggregatedFlameThreadsViewDialog(
    frame: Frame,
    controller: AggregatedFlameThreadSwitchController
) : CloseByEscapeDialog(frame, "Select thread", true) {

    private val model = AggregatedFlameThreadListModel()
    var selectedThreadItem: FlameThreadItem? = null
        private set

    private val table = object : JTable(model) {
        override fun getRowHeight(): Int {
            return max(super.getRowHeight(), THREAD_NAME_HEIGHT)
        }

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }
    }

    init {
        table.columnModel.getColumn(0).preferredWidth = THREAD_NAME_WIDTH
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(evt: MouseEvent) {
                val table = evt.source as JTable
                if (evt.clickCount == 2) {  // Double-click detected
                    val viewRow = table.rowAtPoint(evt.point)
                    setResultAndClose(viewRow)
                    return
                }
                super.mouseClicked(evt)
            }
        })
        val enter: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, SELECT_ACTION)
        table.actionMap.put(SELECT_ACTION, EnterAction())
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.showHorizontalLines = true
        table.gridColor = Color.GRAY

        val scrollPane = JScrollPane(table)
        scrollPane.preferredSize = Dimension(THREAD_NAME_WIDTH + 32, 600)
        add(scrollPane)
        pack()

        model.setData(controller.getThreads())
    }

    private fun setResultAndClose(selected: Int) {
        val modelRowIndex = table.convertRowIndexToModel(selected)
        selectedThreadItem = model.getThreadInfo(modelRowIndex)
        isVisible = false
    }

    private inner class EnterAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            val selected = table.selectedRow
            if (selected < 0) {
                return
            }
            setResultAndClose(selected)
        }
    }
}