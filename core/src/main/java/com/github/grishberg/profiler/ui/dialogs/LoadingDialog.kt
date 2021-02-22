package com.github.grishberg.profiler.ui.dialogs

import com.github.grishberg.profiler.ui.AppIconDelegate
import java.awt.BorderLayout
import java.awt.Frame
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.WindowConstants

class LoadingDialog(
    owner: Frame,
    appIconDelegate: AppIconDelegate
) : JDialog(owner, true) {

    init {
        val panel = JPanel()
        panel.layout = BorderLayout(4, 4)
        panel.border = BorderFactory.createEmptyBorder(32, 32, 32, 32)

        val iconLabel = JLabel()
        appIconDelegate.updateLoadingIcon(iconLabel)

        val label = JLabel("Loading...")
        label.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        panel.add(iconLabel, BorderLayout.CENTER)
        panel.add(label, BorderLayout.PAGE_START)
        setContentPane(panel)
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        pack()
    }
}
