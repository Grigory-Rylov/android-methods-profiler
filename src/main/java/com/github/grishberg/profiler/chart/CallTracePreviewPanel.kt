package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.chart.preview.PREVIEW_IMAGE_HEIGHT
import com.github.grishberg.profiler.common.AppLogger
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JPanel

class CallTracePreviewPanel(
    private val logger: AppLogger
) : JPanel() {
    var image: BufferedImage? = null

    init {
        preferredSize = Dimension(48, PREVIEW_IMAGE_HEIGHT)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        image?.let {
            graphics.drawImage(it, 0, 0, width, PREVIEW_IMAGE_HEIGHT, null)
        }
    }
}
