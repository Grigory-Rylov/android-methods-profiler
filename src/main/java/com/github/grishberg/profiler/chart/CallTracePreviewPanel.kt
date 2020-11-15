package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.common.AppLogger
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JPanel

private const val PANEL_HEIGHT = 32

class CallTracePreviewPanel(
    private val logger: AppLogger
) : JPanel() {
    var image: BufferedImage? = null
        set(value) {
            field = value
            repaint()
        }

    init {
        preferredSize = Dimension(48, PANEL_HEIGHT)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        image?.let {
            graphics.drawImage(it, 0, 0, width, PANEL_HEIGHT, null)
        }
    }
}
