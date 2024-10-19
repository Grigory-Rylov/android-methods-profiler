package com.github.grishberg.profiler.plugins.stages

import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class ResultTablePopupMenu(
    private val navigateAction: () -> Unit,
    private val copyAction: () -> Unit,
    private val copyNameAction: () -> Unit = {},
) : JPopupMenu() {
    private val navigateMenuItem = JMenuItem("Navigate")
    private val copyMenuItem = JMenuItem("Copy")
    private val copyNameMenuItem = JMenuItem("Copy name")

    init {
        add(navigateMenuItem)
        add(copyMenuItem)
        add(copyNameMenuItem)

        navigateMenuItem.addActionListener {
            navigateAction.invoke()
        }
        copyMenuItem.addActionListener {
            copyAction.invoke()
        }
        copyNameMenuItem.addActionListener {
            copyNameAction.invoke()
        }
    }
}
