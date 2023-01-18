package com.github.grishberg.profiler.ui

import com.intellij.openapi.util.IconLoader
import java.awt.Frame
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel

private const val ICON_SIZE = 18

class PluginAppIconDelegate(private val iconSize: Int = ICON_SIZE) : AppIconDelegate {
    override fun updateFrameIcon(frame: Frame) { /* not used */ }

    override fun updateLoadingIcon(iconLabel: JLabel) {
        val imageIcon = IconLoader.getIcon("/images/loading.gif", this.javaClass)
        iconLabel.icon = imageIcon
    }

    override fun loadIcon(path: String, altText: String): Icon {
        val icon = IconLoader.getIcon("/$path", this.javaClass)
        if (icon is ImageIcon) {
            icon.setDescription(altText)
        }
        return icon
    }
}
