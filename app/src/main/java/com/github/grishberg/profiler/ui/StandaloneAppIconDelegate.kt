package com.github.grishberg.profiler.ui

import java.awt.Frame
import java.awt.Toolkit
import javax.swing.ImageIcon
import javax.swing.JLabel

class StandaloneAppIconDelegate : AppIconDelegate {
    private val classLoader = this.javaClass.classLoader

    override fun updateFrameIcon(frame: Frame) {
        val icon = ClassLoader.getSystemResource("images/icon.png")
        frame.iconImage = Toolkit.getDefaultToolkit().getImage(icon)
    }

    override fun updateLoadingIcon(label: JLabel) {
        val imageIcon = loadIcon("images/loading.gif")
        label.icon = imageIcon
        imageIcon.imageObserver = label
    }

    override fun loadIcon(name: String): ImageIcon {
        val imageURL = classLoader.getResource(name)
        return ImageIcon(imageURL)
    }
}
