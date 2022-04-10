package com.github.grishberg.profiler.common

import com.github.grishberg.profiler.core.ProfileData
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class ProfileDataRenderer : JLabel(), TableCellRenderer {
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
        if (value !is ProfileData) {
            return this
        }

        text = value.name
        return this
    }
}
