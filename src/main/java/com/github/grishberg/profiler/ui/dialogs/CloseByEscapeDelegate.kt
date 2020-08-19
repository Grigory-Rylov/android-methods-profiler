package com.github.grishberg.profiler.ui.dialogs

import java.awt.Frame
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*


/**
 * Dialog closed by ESCAPE key.
 */
open class CloseByEscapeDialog(
    owner: Frame, title: String, modal: Boolean
) : JDialog(owner, title, modal) {
    init {
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(we: WindowEvent) {
                onDialogClosed()
                isVisible = false
            }
        })
    }

    override fun createRootPane(): JRootPane {
        val rootPane = JRootPane()
        val stroke = KeyStroke.getKeyStroke("ESCAPE")
        val actionListener: Action = object : AbstractAction() {
            override fun actionPerformed(actionEvent: ActionEvent?) {
                onDialogClosed()
                isVisible = false
            }
        }
        val inputMap: InputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        inputMap.put(stroke, "ESCAPE")
        rootPane.actionMap.put("ESCAPE", actionListener)
        return rootPane
    }

    open fun onDialogClosed() = Unit
}