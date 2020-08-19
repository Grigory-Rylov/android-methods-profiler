package com.github.grishberg.profiler.ui.dialogs.info

import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import javax.swing.table.TableModel
import kotlin.math.max

class JFixedWidthTable(model: TableModel) : JTable(model){
    override fun prepareRenderer(renderer: TableCellRenderer?, row: Int, column: Int): Component {
        val component = super.prepareRenderer(renderer, row, column)
        val rendererWidth = component.preferredSize.width
        val tableColumn: TableColumn = getColumnModel().getColumn(column)
        tableColumn.preferredWidth = max(rendererWidth + intercellSpacing.width, tableColumn.preferredWidth)
        return component
    }
}
