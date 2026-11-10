package com.github.grishberg.profiler.ui.dialogs

import com.github.grishberg.profiler.analyzer.ReportGenerator
import com.github.grishberg.profiler.common.JNumberField
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.ui.Main
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter


class ReportsGeneratorDialog(
    owner: Frame,
    private val settings: SettingsFacade,
    private val reportsGeneratorDelegate: ReportGenerator,
) : CloseByEscapeDialog(owner, "Generate methods report", true), ActionListener {

    private val constructorsCheckbox: JCheckBox
    private val durationLimit: JNumberField
    private val packageFilter: JTextField

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

        constructorsCheckbox = JCheckBox()

        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "constructors only", constructorsCheckbox, "If checked - will be exported only constructors"
        )

        durationLimit = JNumberField(20)
        durationLimit.value = 0
        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "minimum duration", durationLimit, "If checked - will be exported only constructors"
        )

        packageFilter = JTextField(20)
        addLabelAndField(
            content, labelConstraints, fieldConstraints,
            "package filter", packageFilter, "If not empty - show methods with given package prefix"
        )
        fieldConstraints.gridy++
        fieldConstraints.gridwidth = 2

        val button = JButton("Generate")
        button.addActionListener(this)
        content.add(button, fieldConstraints)
        contentPane = content
    }

    override fun onDialogClosed() {
        constructorsCheckbox.isSelected = false
        durationLimit.value = 0
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
        val fileChooser = JFileChooser(settings.reportsFileDialogDir)
        fileChooser.dialogTitle = "Specify a file to save"
        val filter = FileNameExtensionFilter("Text files", "txt")
        fileChooser.fileFilter = filter

        val userSelection = fileChooser.showSaveDialog(this)

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            var fileToSave = fileChooser.selectedFile
            if (fileToSave.extension.lowercase() != "txt") {
                fileToSave = File(fileToSave.absolutePath + ".txt")
            }
            settings.reportsFileDialogDir = fileToSave.parent
            reportsGeneratorDelegate.generate(fileToSave, constructorsCheckbox.isSelected, durationLimit.value as Int, packageFilter.text.trim())
            isVisible = false
        }
    }
}
