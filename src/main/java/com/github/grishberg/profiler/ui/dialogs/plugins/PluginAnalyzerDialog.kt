package com.github.grishberg.profiler.ui.dialogs.plugins

import com.github.grishberg.android.profiler.plugins.CallTraceAnalyzerResult
import com.github.grishberg.profiler.common.CyclicTableRowSorter
import com.github.grishberg.profiler.common.DoubleRenderer
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import com.github.grishberg.profiler.ui.dialogs.info.JFixedWidthTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.border.EmptyBorder

class PluginAnalyzerDialog(
    owner: Frame,
    private val focusElementDelegate: FocusElementDelegate,
    title: String
) : CloseByEscapeDialog(owner, title, false) {
    private val model = CallTraceAnalyzerResultModel()
    private val table = JFixedWidthTable(model)
    private val progressBar = JProgressBar()

    init {
        progressBar.isIndeterminate = true
        val listScroll = JScrollPane(table)
        val sorter = CyclicTableRowSorter(table.model)
        table.rowSorter = sorter
        sorter.setComparator(1, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(2, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(3, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(4, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        table.setDefaultRenderer(Double::class.java, DoubleRenderer())

        createTableDoubleClickListener()

        val content = JPanel().apply {
            layout = BorderLayout()
            add(listScroll, BorderLayout.CENTER)
            add(progressBar, BorderLayout.SOUTH)
            preferredSize = Dimension(800, 500)
            border = EmptyBorder(8, 8, 8, 8)
        }
        contentPane = content
        pack()
    }

    private fun createTableDoubleClickListener() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(evt: MouseEvent) {
                val table = evt.source as JFixedWidthTable
                if (evt.clickCount == 2) { // Double-click detected
                    val viewRow = table.rowAtPoint(evt.point)
                    val modelRowIndex = table.convertRowIndexToModel(viewRow)
                    focusElementDelegate.selectProfileElement(model.itemAt(modelRowIndex).method)
                    return
                }
                super.mouseClicked(evt)
            }
        })
    }

    fun showProgress() {
        progressBar.isVisible = true
    }

    fun showResult(result: CallTraceAnalyzerResult) {
        progressBar.isVisible = false
        model.setItems(result.foundMethods())
    }
}
