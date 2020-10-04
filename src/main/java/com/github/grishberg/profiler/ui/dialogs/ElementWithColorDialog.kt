package com.github.grishberg.profiler.ui.dialogs

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.Frame
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * Allows to create and edit title and color.
 */
class ElementWithColorDialog : CloseByEscapeDialog, ChangeListener {
    private val colors = listOf(
        Color(0x7678ed),
        Color(0xf7b801),
        Color(0xf18701),
        Color(0xf35b04),
        Color(0xafd5aa),
        Color(0x8c6057),
        Color(0xbf4342),
        Color(0xa1c181)
    )
    private var colorChooser: JColorChooser
    private var name: JTextField
    private var selectedColor = colors.random()
    var result: Result? = null
        private set

    constructor(parent: Frame, title: String, modal: Boolean) : super(parent, title, modal)

    constructor(parent: Dialog, title: String, modal: Boolean) : super(parent, title, modal)

    init {
        val content = JPanel()
        content.border = EmptyBorder(4, 4, 4, 4)
        content.layout = BorderLayout()

        name = JTextField(10)
        name.addActionListener {
            closeAfterSuccess()
        }
        content.add(name, BorderLayout.PAGE_START)
        colorChooser = JColorChooser(selectedColor)
        colorChooser.selectionModel.addChangeListener(this)
        content.add(colorChooser, BorderLayout.CENTER)

        val okButton = JButton("OK")
        content.add(okButton, BorderLayout.PAGE_END)
        okButton.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                closeAfterSuccess()
            }
        })
        contentPane = content

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(ce: ComponentEvent) {
                name.requestFocusInWindow()
            }
        })
        pack()
    }

    private fun closeAfterSuccess() {
        result = Result(name.text, selectedColor)
        isVisible = false
    }

    override fun onDialogClosed() {
        result = null
    }

    override fun stateChanged(e: ChangeEvent) {
    }

    fun showDialog(relativeTo: JComponent) {
        setLocationRelativeTo(relativeTo)
        isVisible = true
    }

    fun showForEditMode(color: Color, title: String, relativeTo: JComponent) {
        selectedColor = color
        colorChooser.color = color
        name.text = title
        showDialog(relativeTo)
    }

    data class Result(val title: String, val color: Color)
}
