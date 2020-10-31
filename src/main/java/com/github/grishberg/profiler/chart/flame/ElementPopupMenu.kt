package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.common.createControlAccelerator
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class ElementPopupMenu(
    controller: FlameChartController
) : JPopupMenu() {
    val copyItem = JMenuItem("Copy").apply {
        accelerator = createControlAccelerator('C')
    }

    init {
        add(copyItem)
        copyItem.addActionListener {
            controller.copySelectedToClipboard()
        }
    }
}