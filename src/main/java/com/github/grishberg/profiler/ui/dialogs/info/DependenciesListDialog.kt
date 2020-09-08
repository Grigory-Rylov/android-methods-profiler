package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.profiler.analyzer.ProfileData
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.util.Comparator
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.border.EmptyBorder
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter


internal class DependenciesListDialog(
    owner: Frame,
    private val logic: DependenciesDialogLogic
) : CloseByEscapeDialog(owner, "Selected methods", false) {
    private val dependenciesListModel = ElementsModel()
    private val table = JFixedWidthTable(dependenciesListModel)
    private val statusLabel = JLabel()
    private val onlyConstructorsCheckbox = JCheckBox("Only constructors")
    private var lastSelectedItems: List<ProfileData> = emptyList()

    init {
        val listScroll = JScrollPane(table)
        val sorter = CyclicTableRowSorter(table.model)
        val copyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMask, false)
        table.registerKeyboardAction(CopyAction(), "Copy", copyStroke, JComponent.WHEN_FOCUSED)

        table.rowSorter = sorter
        sorter.setComparator(1, Comparator<Double> { v1, v2 ->
            v1.compareTo(v2)
        })
        sorter.setComparator(2, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(3, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(4, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(evt: MouseEvent) {
                val table = evt.source as JFixedWidthTable
                if (evt.clickCount == 2) { // Double-click detected
                    val viewRow = table.rowAtPoint(evt.point)
                    val modelRowIndex = table.convertRowIndexToModel(viewRow)
                    logic.onProfileDataSelected(dependenciesListModel.itemAt(modelRowIndex))
                    return
                }
                super.mouseClicked(evt)
            }
        })
        table.setDefaultRenderer(Double::class.java, DoubleRenderer())

        val copyButton = JButton("Copy").apply {
            addActionListener {
                logic.copyToClipboard()
            }
        }

        val saveToFileButton = JButton("Export to file").apply {
            addActionListener {
                logic.saveToFile()
            }
        }

        val buttonsPanel = JPanel().apply {
            add(copyButton)
            add(saveToFileButton)
            add(onlyConstructorsCheckbox)
        }

        val statusPanel = JPanel().apply {
            layout = BorderLayout()
            border = BevelBorder(BevelBorder.LOWERED)
            add(statusLabel, BorderLayout.CENTER)
        }

        val bottomPanel = JPanel().apply {
            layout = BorderLayout()
            add(buttonsPanel, BorderLayout.CENTER)
            add(statusPanel, BorderLayout.SOUTH)
        }
        val content = JPanel().apply {
            layout = BorderLayout()
            add(listScroll, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
            preferredSize = Dimension(640, 500)
            border = EmptyBorder(8, 8, 8, 8)
        }

        setupConstructorsFilterCheckbox()

        contentPane = content
        pack()
    }

    private fun setupConstructorsFilterCheckbox() {
        onlyConstructorsCheckbox.addItemListener {
            if (onlyConstructorsCheckbox.isSelected) {
                val filtered = lastSelectedItems.filter { it.name.endsWith(".<init>") }
                updateItemsList(filtered)
            } else {
                updateItemsList(lastSelectedItems)
            }
        }
    }

    fun setDependencies(items: List<ProfileData>) {
        lastSelectedItems = items
        updateItemsList(items)
    }

    private fun updateItemsList(items: List<ProfileData>) {
        dependenciesListModel.setItems(items)
        statusLabel.text = "Items count: ${items.size}"
        repaint()
    }

    private class DoubleRenderer : JLabel(), TableCellRenderer {
        init {
            isOpaque = true
        }

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            if (value !is Double) {
                return this
            }

            text = "%.3f".format(value)
            return this
        }
    }

    private inner class CopyAction : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            if (e.actionCommand.compareTo("Copy") != 0) {
                return
            }
            val sbf = StringBuffer()
            val numcols: Int = table.columnModel.columnCount
            val numRows: Int = table.selectedRowCount

            if (numRows < 1) {
                JOptionPane.showMessageDialog(
                    null, "Invalid Copy Selection",
                    "Invalid Copy Selection", JOptionPane.ERROR_MESSAGE
                )
                return
            }

            for (y in table.selectedRows.indices) {
                val modelIndex = table.convertRowIndexToModel(table.selectedRows[y])
                for (x in 0 until numcols) {
                    if (x == 0) {
                        sbf.append(table.getValueAt(modelIndex, x))
                    } else {
                        sbf.append(String.format("%.3f", table.getValueAt(modelIndex, x)))
                    }
                    if (x < numcols - 1) {
                        sbf.append("\t")
                    }
                }
                if (y < numRows - 1) {
                    sbf.append("\n")
                }
            }

            val stringSelection = StringSelection(sbf.toString())
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }
    }

    class CyclicTableRowSorter(model: TableModel) : TableRowSorter<TableModel>(model) {

        override fun toggleSortOrder(column: Int) {
            val sortKeys: List<SortKey?> = getSortKeys()
            if (sortKeys.isNotEmpty()) {
                if (sortKeys[0]?.sortOrder === SortOrder.DESCENDING) {
                    setSortKeys(null)
                    return
                }
            }
            super.toggleSortOrder(column)
        }
    }
}
