package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.expandabletree.JTreeTable
import com.github.grishberg.profiler.common.CyclicTableRowSorter
import com.github.grishberg.profiler.plugins.stages.methods.MethodsWithStageModel
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.border.BevelBorder
import javax.swing.border.EmptyBorder


interface DialogListener {
    fun onProfileDataSelected(method: ProfileData)
    fun copyToClipboard()
    fun onExportReportToFileClicked()
    fun openStagesFile()
    fun startAnalyze()
    fun onSaveStagesClicked()
    fun onShouldHideUnknownChanged()
    fun onHierarchicalModeChanged()
}

class StageAnalyzerDialog(
    owner: Frame
) : CloseByEscapeDialog(owner, "Stage analyzer", false) {

    private var model = MethodsWithStageModel(emptyList())
    private val table = JTreeTable(model)
    private val startButton = JButton("Analyze").apply { isEnabled = false }
    private val exportToFileButton = JButton("Export report to file").apply { isEnabled = false }
    private val saveStagesButton = JButton("Save stages").apply { isEnabled = false }
    private val shouldHideUnknown = JCheckBox("Hide unknown")
    private val hierarchical = JCheckBox("Hierarchical")
    private val statusLabel = JLabel()
    var dialogListener: DialogListener? = null

    init {
        val listScroll = JScrollPane(table)
        val sorter = CyclicTableRowSorter(table.model)
        val copyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMask, false)
        table.registerKeyboardAction(CopyAction(), "Copy", copyStroke, JComponent.WHEN_FOCUSED)

        val tableRenderer = table.treeTableCellRenderer
        tableRenderer.isRootVisible = false

        //table.rowSorter = sorter
        sorter.setComparator(1, Comparator<Double> { v1, v2 ->
            v1.compareTo(v2)
        })
        sorter.setComparator(2, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(3, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        sorter.setComparator(4, Comparator<Double> { v1, v2 -> v1.compareTo(v2) })
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                handleShowMenu(e)
            }

            private fun handleShowMenu(e: MouseEvent) {
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    val navigateAction = {
                        val path = table.treeTableCellRenderer.selectionPath?.lastPathComponent
                        if (path is ProfileData) {
                            dialogListener?.onProfileDataSelected(path)
                        }
                        if (path is WrongStage) {
                            dialogListener?.onProfileDataSelected(path.method)
                        }
                    }
                    val menu = ResultTablePopupMenu({ copyToClipboard() }, navigateAction)
                    menu.show(table, e.x, e.y)
                }
            }
        })

        exportToFileButton.addActionListener {
            dialogListener?.onExportReportToFileClicked()
        }

        val openStagesFileButton = JButton("Open stages file").apply {
            addActionListener {
                dialogListener?.openStagesFile()
            }
        }

        startButton.addActionListener {
            dialogListener?.startAnalyze()
        }

        saveStagesButton.addActionListener {
            dialogListener?.onSaveStagesClicked()
        }

        shouldHideUnknown.addActionListener {
            dialogListener?.onShouldHideUnknownChanged()
        }

        hierarchical.addActionListener {
            dialogListener?.onHierarchicalModeChanged()
        }

        val actionButtons = JPanel().apply {
            add(startButton)
            add(exportToFileButton)
            add(Box.createHorizontalStrut(5))
            add(openStagesFileButton)
            add(saveStagesButton)
            add(Box.createHorizontalStrut(5))
            add(shouldHideUnknown)
            add(hierarchical)
        }

        val statusPanel = JPanel().apply {
            layout = BorderLayout()
            border = BevelBorder(BevelBorder.LOWERED)
            add(statusLabel, BorderLayout.CENTER)
        }

        val bottomPanel = JPanel().apply {
            layout = BorderLayout()
            add(actionButtons, BorderLayout.CENTER)
            add(statusPanel, BorderLayout.SOUTH)
        }
        val content = JPanel().apply {
            layout = BorderLayout()
            add(listScroll, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
            preferredSize = Dimension(840, 500)
            border = EmptyBorder(8, 8, 8, 8)
        }

        contentPane = content
        pack()
    }

    fun updateTitle(title: String) {
        setTitle(title)
    }

    fun showDialog() {
        setLocationRelativeTo(parent)
        isVisible = true
    }

    fun disableSaveStagesButton() {
        saveStagesButton.isEnabled = false
    }

    fun enableSaveStagesButton() {
        saveStagesButton.isEnabled = true
    }

    fun enableStartButton() {
        startButton.isEnabled = true
    }

    fun enableExportButtons() {
        exportToFileButton.isEnabled = true
    }

    fun showResult(result: List<WrongStage>) {
        table.isEnabled = true
        model = MethodsWithStageModel(result)
        table.setModel(model)
        startButton.isEnabled = true
    }

    fun showProgress() {
        table.isEnabled = false
        startButton.isEnabled = false
    }

    fun shouldHideUnknown(): Boolean {
        return shouldHideUnknown.isSelected
    }

    fun checkHideUnknownCheckbox(checked: Boolean) {
        shouldHideUnknown.isSelected = checked
    }

    fun hierarchical(): Boolean {
        return hierarchical.isSelected
    }

    fun checkHierarchicalCheckbox(checked: Boolean) {
        hierarchical.isSelected = checked
    }

    private inner class CopyAction : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            if (e.actionCommand.compareTo("Copy") != 0) {
                return
            }
            copyToClipboard()
        }
    }

    private fun copyToClipboard() {
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
                if (model.columnClass[x] == Double::class.java) {
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

        val stringSelection = StringSelection(sbf.toString())
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(stringSelection, null)
    }
}
