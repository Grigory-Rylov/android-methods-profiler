package com.github.grishberg.profiler.plugins.stages.constructors

import com.github.grishberg.profiler.plugins.stages.MethodNavigationAction
import com.github.grishberg.profiler.plugins.stages.ResultTablePopupMenu
import com.github.grishberg.profiler.plugins.stages.WrongStage
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.TableRowSorter


class EarlyConstructorsResultDialog(owner: Dialog) : CloseByEscapeDialog(owner, "Early constructors", false) {

    private val model = EarlyConstructorsTableModel()
    private val table = JTable()
    private val filterTextField = JTextField(20)
    private val statusLabel = JLabel()
    private val sorter = TableRowSorter(model)

    var dialogListener: MethodNavigationAction? = null

    init {
        val content = JPanel().apply {
            layout = BorderLayout()
            add(buildScrollPanel(), BorderLayout.CENTER)
            add(buildBottomPanel(), BorderLayout.SOUTH)
        }
        contentPane = content
        pack()

        addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                updateColumnWidth()
            }

            override fun componentMoved(e: ComponentEvent?) = Unit

            override fun componentShown(e: ComponentEvent?) = Unit

            override fun componentHidden(e: ComponentEvent?) = Unit
        })
    }


    private fun buildScrollPanel(): JComponent {
        table.setDefaultEditor(Object::class.java, null)
        table.model = model
        table.rowSorter = sorter
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                handleShowMenu(e)
            }

            private fun handleShowMenu(e: MouseEvent) {
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    val selectedItem = table.selectedRows[0]
                    val convertedItemIndex = table.convertRowIndexToModel(selectedItem)
                    if (convertedItemIndex < 0) {
                        return
                    }

                    val navigateAction = {
                        val selectedCol = model.methodDataList[convertedItemIndex]
                        dialogListener?.onProfileDataSelected(selectedCol.method)
                        Unit
                    }
                    val menu = ResultTablePopupMenu(
                        copyAction = { copyToClipboard() },
                        copyNameAction = { copyToClipboard(onlyName = true) },
                        navigateAction = navigateAction,
                    )
                    menu.show(table, e.x, e.y)
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val target = e.source as JTable
                    val row = target.selectedRow
                    val convertedItemIndex = table.convertRowIndexToModel(row)
                    if (convertedItemIndex < 0) {
                        return
                    }

                    val selectedCol = model.methodDataList[convertedItemIndex]
                    dialogListener?.onProfileDataSelected(selectedCol.method)
                }
            }
        })
        return JScrollPane(table)
    }

    private fun buildBottomPanel(): JPanel {
        val filterPanel = JPanel().apply {
            layout = FlowLayout()
            add(filterTextField)
            //add(filterOkButton)
        }

        filterTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                filter(filterTextField.text)
            }

            override fun removeUpdate(e: DocumentEvent) {
                filter(filterTextField.text)
            }

            override fun changedUpdate(e: DocumentEvent) {
                filter(filterTextField.text)
            }
        })

        val statusPanel = JPanel().apply {
            layout = FlowLayout()
            add(statusLabel)
        }

        return JPanel().apply {
            layout = BorderLayout()
            add(filterPanel, BorderLayout.CENTER)
            add(statusPanel, BorderLayout.SOUTH)
        }
    }

    private fun filter(newText: String) {
        val text = newText.trim()
        if (text.isEmpty()) {
            sorter.rowFilter = null
        } else {
            sorter.rowFilter = RowFilter.regexFilter("(?i)$text", 0)
        }
        table.rowSorter = sorter
    }

    private fun updateColumnWidth() {
        val tableColumnModel = table.columnModel
        val selfTimeColumn = tableColumnModel.getColumn(1)
        val startTimeColumn = tableColumnModel.getColumn(2)
        val tableWidth = table.width
        println("table width = ${tableWidth}")

        val selfTimeWidth = 50 // Ширина второго столбца
        val startTimeWidth = 50 // Ширина третьего столбца

        val remainingWidth = tableWidth - selfTimeWidth - startTimeWidth
        val nameWidth = if (remainingWidth > 0) remainingWidth else 0

        selfTimeColumn.preferredWidth = selfTimeWidth
        startTimeColumn.preferredWidth = startTimeWidth
        val nameColumn = tableColumnModel.getColumn(0)
        nameColumn.preferredWidth = nameWidth

        filterTextField.preferredSize = Dimension(tableWidth - 16, filterTextField.preferredSize.height)
    }

    fun setData(data: List<WrongStage>) {
        statusLabel.text = "found ${data.size} items"
        model.setData(data)
        table.model = model
        updateColumnWidth()
    }

    private inner class CopyAction : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            if (e.actionCommand.compareTo("Copy") != 0) {
                return
            }
            copyToClipboard()
        }
    }

    private fun copyToClipboard(onlyName: Boolean = false) {
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

        if (onlyName) {
            for (y in table.selectedRows.indices) {
                val modelIndex = table.convertRowIndexToModel(table.selectedRows[y])
                sbf.append(table.getValueAt(modelIndex, 0))
                if (y < numRows - 1 && table.selectedRows.size > 1) {
                    sbf.append("\n")
                }
            }
        } else {

            for (y in table.selectedRows.indices) {
                val modelIndex = table.convertRowIndexToModel(table.selectedRows[y])
                for (x in 0 until numcols) {
                    if (model.getColumnClass(x) == Double::class.java) {
                        sbf.append(String.format("%.3f", table.getValueAt(modelIndex, x)))
                    } else {
                        sbf.append(table.getValueAt(modelIndex, x))
                    }
                    if (x < numcols - 1) {
                        sbf.append("\t")
                    }
                }
                if (y < numRows - 1) {
                    sbf.append("\n")
                }
            }

        }

        val stringSelection = StringSelection(sbf.toString())
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(stringSelection, null)
    }

}
