package com.github.grishberg.profiler.chart.threads

import com.github.grishberg.profiler.chart.preview.PREVIEW_IMAGE_HEIGHT
import com.github.grishberg.profiler.chart.preview.PREVIEW_IMAGE_WIDTH
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.core.ThreadItem
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.Color
import java.awt.Dimension
import java.awt.Frame
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import kotlin.math.max

private const val THREAD_NAME_WIDTH = 180
private const val SELECT_ACTION = "Select"

class ThreadsViewDialog(
    title: String,
    frame: Frame,
    controller: ThreadsSelectionController,
    previewImageRepository: PreviewImageRepository,
    private val logger: AppLogger
) : CloseByEscapeDialog(frame, title, true), ThreadsSelectionView {

    private val model = ThreadListModel(previewImageRepository)
    var selectedThreadItem: ThreadItem? = null
        private set

    private var _selectedRow: Int = -1
    private var _verticalScrollPosition: Int = 0

    val selectedRow: Int
        get() = _selectedRow

    val scrollPosition: Int
        get() = _verticalScrollPosition

    private val table = object : JTable(model) {
        override fun getRowHeight(): Int {
            return max(super.getRowHeight(), PREVIEW_IMAGE_HEIGHT)
        }

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }
    }
    private val scrollPane: JScrollPane

    init {
        controller.view = this
        val renderer = ThreadListRenderer()
        table.setDefaultRenderer(ThreadListModel.ImageHolder::class.java, renderer)
        table.columnModel.getColumn(0).preferredWidth = THREAD_NAME_WIDTH
        table.columnModel.getColumn(1).preferredWidth = PREVIEW_IMAGE_WIDTH
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(evt: MouseEvent) {
                val table = evt.source as JTable
                if (evt.clickCount == 2) { // Double-click detected
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

        scrollPane = JScrollPane(table)
        scrollPane.preferredSize = Dimension(PREVIEW_IMAGE_WIDTH + THREAD_NAME_WIDTH + 32, 600)
        add(scrollPane)
        pack()
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

    private fun setResultAndClose(selected: Int) {
        val viewport = scrollPane.viewport
        _selectedRow = selected
        _verticalScrollPosition = viewport.viewPosition.y;
        val modelRowIndex = table.convertRowIndexToModel(selected)
        selectedThreadItem = model.getThreadInfo(modelRowIndex)
        isVisible = false
    }

    override fun showThreads(threads: List<ThreadItem>) {
        model.setData(threads)
    }

    fun restoreSelection(selectedRow: Int, verticalScrollPosition: Int) {
        if (selectedRow < 0) {
            return
        }
        table.setRowSelectionInterval(selectedRow, selectedRow)
        val viewport: JViewport = scrollPane.viewport
        viewport.viewPosition = Point(0, verticalScrollPosition)
    }
}
