package com.github.grishberg.profiler.chart.threads

import java.awt.Component
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer


class ThreadListRenderer : DefaultTableCellRenderer() {

    init {
        horizontalTextPosition = JLabel.CENTER
        verticalTextPosition = JLabel.BOTTOM
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        col: Int
    ): Component? {
        val r: JLabel = super.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, col
        ) as JLabel
        if (value !is ThreadListModel.ImageHolder) {
            return r
        }
        text = null
        if (value.image == null) {
            text = "rendering..."
            return r
        }
        icon = ImageIcon(value.image)
        return r
    }
}
