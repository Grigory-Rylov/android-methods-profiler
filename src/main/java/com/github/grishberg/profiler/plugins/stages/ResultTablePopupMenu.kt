package com.github.grishberg.profiler.plugins.stages

import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class ResultTablePopupMenu(
    private val copyAction: () -> Unit,
    private val navigateAction: () -> Unit
) : JPopupMenu() {
    private val copyMenuItem = JMenuItem("Copy")
    private val navigateMenuItem = JMenuItem("Navigate")

    init {
        add(navigateMenuItem)
        add(copyMenuItem)

        navigateMenuItem.addActionListener {
            navigateAction.invoke()
        }
        copyMenuItem.addActionListener {
            copyAction.invoke()
        }
    }
}
