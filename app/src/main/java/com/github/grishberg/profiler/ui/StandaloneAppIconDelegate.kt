package com.github.grishberg.profiler.ui

import java.awt.Frame
import java.awt.Toolkit
import javax.swing.ImageIcon
import javax.swing.JLabel

class StandaloneAppIconDelegate : AppIconDelegate {
    override fun updateFrameIcon(frame: Frame) {
        val icon = ClassLoader.getSystemResource("images/icon.png")
        frame.iconImage = Toolkit.getDefaultToolkit().getImage(icon)
    }

    override fun updateLoadingIcon(label: JLabel) {
        val cldr = this.javaClass.classLoader
        val imageURL = cldr.getResource("images/loading.gif")
        val imageIcon = ImageIcon(imageURL)
        label.setIcon(imageIcon)
        imageIcon.imageObserver = label
    }
}
