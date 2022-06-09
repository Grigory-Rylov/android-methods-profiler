package com.github.grishberg.profiler.ui.dialogs

import com.github.grishberg.profiler.common.JNumberField
import com.github.grishberg.profiler.ui.LabeledGridBuilder
import java.awt.Frame
import javax.swing.JButton
import javax.swing.JOptionPane


class ScaleRangeDialog(
    owner: Frame,
) : CloseByEscapeDialog(owner, "Set view range", true) {
    private val startRangeField: JNumberField
    private val endRangeField: JNumberField
    var result: Range? = null

    init {
        startRangeField = JNumberField(10)
        startRangeField.value = 0

        endRangeField = JNumberField(10)
        endRangeField.value = 0
        endRangeField.addActionListener {
            applyRange()
        }

        val okButton = JButton("OK")
        okButton.addActionListener {
            applyRange()
        }

        val builder = LabeledGridBuilder()
        builder.addLabeledComponent("Left range in ms ", startRangeField)
        builder.addLabeledComponent("Right range in ms ", endRangeField)
        builder.addSingleComponent(okButton)

        contentPane = builder.content
        pack()
    }

    override fun onDialogClosed() {
        result = null
    }

    private fun applyRange() {
        val start = startRangeField.value
        val end = endRangeField.value
        if (start >= end) {
            JOptionPane.showMessageDialog(this, "Left range must be less than right")
            return
        }

        result = Range(start.toDouble(), end.toDouble())
        isVisible = false
    }

    data class Range(val start: Double, val end: Double)
}
