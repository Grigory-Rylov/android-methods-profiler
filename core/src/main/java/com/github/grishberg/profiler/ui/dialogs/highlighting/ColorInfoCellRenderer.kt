package com.github.grishberg.profiler.ui.dialogs.highlighting

import com.github.grishberg.profiler.chart.highlighting.ColorInfo
import com.github.grishberg.profiler.common.contrastColor
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class ColorInfoCellRenderer : JLabel(), ListCellRenderer<ColorInfo> {
    init {
        isOpaque = true
    }

    override fun getListCellRendererComponent(
        list: JList<out ColorInfo>,
        value: ColorInfo,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        text = value.filter
        font = list.font
        isEnabled = list.isEnabled
        border = if (isSelected && cellHasFocus) {
            border
        } else {
            null
        }

        if (isSelected) {
            background = list.selectionBackground
            foreground = list.selectionForeground
        } else {
            background = value.color
            foreground = contrastColor(value.color)
        }
        return this
    }
}
