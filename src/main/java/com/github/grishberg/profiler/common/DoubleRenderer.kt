package com.github.grishberg.profiler.common

import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class DoubleRenderer : JLabel(), TableCellRenderer {
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
