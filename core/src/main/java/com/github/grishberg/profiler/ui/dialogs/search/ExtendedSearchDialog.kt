package com.github.grishberg.profiler.ui.dialogs.search

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.*
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.EmptyBorder

class ExtendedSearchDialog(owner: Frame) :
    CloseByEscapeDialog(owner, "Extended search", false),
    ActionListener {
    interface OnSearchActionListener {
        fun onSearchLaunched(
            methodText: String,
            parentMask: String?,
            isDirectParent: Boolean,
            isOnlyCurrentThread: Boolean,
            ignoreCase: Boolean
        )
    }

    private val name: JTextField
    private val parentMask: JTextField
    private val isDirectParent: JCheckBox
    private val onlyCurrentThread: JCheckBox

    var listener: OnSearchActionListener? = null

    init {
        val content = JPanel()
        content.border = EmptyBorder(8, 8, 8, 8)
        content.layout = GridBagLayout()

        val fieldConstraints = GridBagConstraints()
        val labelConstraints = GridBagConstraints()
        labelConstraints.weightx = 0.0
        labelConstraints.gridwidth = 1
        labelConstraints.gridy = 0
        labelConstraints.gridx = 0

        fieldConstraints.gridwidth = 2
        fieldConstraints.weightx = 1.0
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL

        isDirectParent = JCheckBox()
        onlyCurrentThread = JCheckBox()

        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Is direct parent", isDirectParent, "If checked - find methods with direct parent, found by mask"
        )
        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Search in current thread", onlyCurrentThread, "If checked - find methods only in current thread"
        )

        parentMask = JTextField(20)
        name = JTextField(20)
        parentMask.addActionListener(this)
        name.addActionListener(this)

        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Search method", name, "What you want to find?"
        )
        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Parent  mask", parentMask, "Parent of searched methods"
        )

        fieldConstraints.gridy++
        fieldConstraints.gridwidth = 2

        val button = JButton("Search")
        button.addActionListener(this)
        content.add(button, fieldConstraints)
        contentPane = content
        pack()
    }

    override fun onDialogClosed() {
        isDirectParent.isSelected = false
        onlyCurrentThread.isSelected = false
    }

    private fun addLabelAndField(
        content: JPanel,
        labelConstraints: GridBagConstraints,
        fieldConstraints: GridBagConstraints,
        labelText: String,
        field: JComponent,
        fieldTooltip: String
    ) {

        content.add(JLabel(labelText), labelConstraints)
        labelConstraints.gridy++

        fieldConstraints.gridx = 1
        fieldConstraints.gridy++
        field.toolTipText = fieldTooltip
        content.add(field, fieldConstraints)
    }

    override fun actionPerformed(e: ActionEvent?) {
        val methodText = name.text.trim()
        if (methodText.isEmpty()) {
            // TODO: show error message
            return
        }
        val parentMethodText = parentMask.text.trim()
        listener?.onSearchLaunched(
            methodText = methodText,
            parentMask = parentMethodText,
            isDirectParent = isDirectParent.isSelected,
            isOnlyCurrentThread = onlyCurrentThread.isSelected,
            ignoreCase = false,
        )
    }
}
