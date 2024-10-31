package com.github.grishberg.profiler.plugins.stages.constructors

import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import java.awt.Dialog
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.EmptyBorder

class EarlyConstructorsSearchDialog(
    owner: Frame,
    private val logic: EarlyConstructorsLogicImpl,
) : CloseByEscapeDialog(owner, "Should be lazy.", false), ActionListener, EarlyConstructorsUi {

    private val currentSection = JComboBox(emptyArray<Stage>())
    private val parentMask: JTextField = JTextField(20)
    private val methodsMask: JTextField = JTextField(20)
    private val searchUsageInOtherThreads = JCheckBox()
    private val openStagesFileButton = JButton("Open stages file").apply {
        addActionListener {
            logic.openStagesFile()
        }
    }
    private val startButton = JButton("Search").apply { isEnabled = false }
    override val owner: Dialog
        get() = this

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


        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Open stages data", this.openStagesFileButton, "Import stages from json file"
        )

        addLabelAndField(
            content,
            labelConstraints,
            fieldConstraints,
            "Search in section",
            currentSection,
            "Select section where need to find constructor calls without members access."
        )

        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Method  mask", methodsMask, "Methods mask"
        )
        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "Parent  mask", parentMask, "Parent of searched methods"
        )

        addLabelAndField(
            content,
            labelConstraints,
            fieldConstraints,
            "Search usage in other threads",
            searchUsageInOtherThreads,
            "When checked - search class usage in other threads"
        )

        fieldConstraints.gridy++
        fieldConstraints.gridwidth = 2

        startButton.addActionListener(this)
        content.add(startButton, fieldConstraints)
        contentPane = content
        pack()
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

    override fun updateTitle(title: String) {
        setTitle(title)
    }

    override fun setStages(stages: List<Stage>) {
        currentSection.model = SelectStageComboBoxModel(stages)
        if (stages.isNotEmpty()) {
            startButton.isEnabled = true
        }
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (currentSection.selectedItem != null) {
            startButton.isEnabled = false

            val parentMethodText = parentMask.text.trim()
            val methodText = methodsMask.text.trim()
            logic.startSearch(
                currentSection.selectedItem as Stage,
                methodText,
                parentMethodText,
                searchUsageInOtherThreads.isSelected
            )
        }
    }

    override fun onCalculated() {
        startButton.isEnabled = true
    }
}
