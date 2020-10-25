package com.github.grishberg.profiler.common

import com.github.grishberg.expandabletree.TreeTableModel
import com.github.grishberg.profiler.plugins.stages.WrongStage
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class TreeTableModelRenderer : JLabel(), TableCellRenderer {
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
        if (value !is TreeTableModel) {
            return this
        }

        //text = value.method.name
        return this
    }
}
