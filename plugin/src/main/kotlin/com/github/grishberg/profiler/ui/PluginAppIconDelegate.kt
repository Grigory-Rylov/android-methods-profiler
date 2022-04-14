package com.github.grishberg.profiler.ui

import com.android.tools.adtui.ImageUtils
import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import java.awt.Frame
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JLabel

private const val ICON_SIZE = 18

class PluginAppIconDelegate(private val iconSize: Int = ICON_SIZE) : AppIconDelegate {
    override fun updateFrameIcon(frame: Frame) { /* not used */ }

    override fun updateLoadingIcon(iconLabel: JLabel) {
        val imageIcon = IconLoader.getIcon("/images/loading.gif", this.javaClass)
        iconLabel.icon = imageIcon
    }

    override fun loadIcon(path: String, altText: String): ImageIcon {
        val icon = IconLoader.getIcon("/$path", this.javaClass)
        val image: BufferedImage = ImageUtil.toBufferedImage(IconUtil.toImage(icon))
        if (UIUtil.isRetina()) {
            val retinaIcon = getRetinaIcon(image)
            if (retinaIcon != null) {
                return retinaIcon
            }
        }
        return ImageIcon(image, altText)
    }

    private fun getRetinaIcon(image: BufferedImage) =
        takeIf { UIUtil.isRetina() }?.let { ImageUtils.convertToRetina(image) }?.let { RetinaImageIcon(it) }
}
