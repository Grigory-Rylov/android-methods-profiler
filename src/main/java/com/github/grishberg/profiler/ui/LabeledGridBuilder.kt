package com.github.grishberg.profiler.ui

import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class LabeledGridBuilder {
    val content = JPanel()

    private val labelConstraints = GridBagConstraints()
    private val fieldConstraints = GridBagConstraints()

    init {
        content.border = EmptyBorder(8, 8, 8, 8)
        content.layout = GridBagLayout()

        labelConstraints.weightx = 0.0
        labelConstraints.gridwidth = 1
        labelConstraints.gridy = 0
        labelConstraints.gridx = 0

        fieldConstraints.fill = GridBagConstraints.HORIZONTAL
        fieldConstraints.gridy = 0
    }

    fun addLabeledComponent(labelText: String, component: JComponent) {
        addLabeledComponent(JLabel(labelText), component)
    }

    fun addLabeledComponent(label: JLabel, component: JComponent) {
        fieldConstraints.gridwidth = 1
        content.add(label, labelConstraints)

        content.add(component, fieldConstraints)

        labelConstraints.gridy++
        fieldConstraints.gridy++
    }

    fun addSingleComponent(component: JComponent) {
        fieldConstraints.gridwidth = 2
        content.add(component, fieldConstraints)
        fieldConstraints.gridy++
        labelConstraints.gridy++
    }
}