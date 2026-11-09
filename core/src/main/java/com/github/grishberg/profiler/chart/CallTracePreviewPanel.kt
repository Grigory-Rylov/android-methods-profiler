package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.SimpleMouseListener
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.math.max

private const val PANEL_HEIGHT = 32
private const val MIN_WINDOW_PX = 2
private const val LABEL_PADDING = 4
private val WINDOW_OVERLAY_FILL = Color(80, 140, 255, 70)
private val WINDOW_LABEL_FALLBACK_COLOR = Color.WHITE

interface OnPreviewClickedAction {
    fun onPreviewClicked(offsetInPercent: Double)
}

data class PreviewWindowRange(
    val startFraction: Double,
    val endFraction: Double,
    val label: String,
)

class CallTracePreviewPanel(
    private val logger: AppLogger
) : JPanel() {
    var previewClickedAction: OnPreviewClickedAction? = null

    var windowRange: PreviewWindowRange? = null
        set(value) {
            field = value
            repaint()
        }

    var image: BufferedImage? = null
        set(value) {
            field = value
            repaint()
        }

    private val windowLabelFont = Font(Font.SANS_SERIF, Font.PLAIN, 10)

    init {
        preferredSize = Dimension(48, PANEL_HEIGHT)
        addMouseListener(object : SimpleMouseListener() {
            override fun mouseLeftClicked(e: MouseEvent) {
                previewClickedAction?.onPreviewClicked(e.x / width.toDouble())
            }
        })
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        graphics.color = UIManager.getColor("Panel.background")
        graphics.fillRect(0, 0, width, height)

        image?.let {
            graphics.drawImage(it, 0, 0, width, height, null)
        }
        drawWindowRange(graphics)
    }

    private fun drawWindowRange(g: Graphics) {
        val range = windowRange ?: return
        val left = (range.startFraction * width).toInt().coerceIn(0, max(0, width - 1))
        val right = (range.endFraction * width).toInt().coerceIn(left + 1, width)
        val windowWidth = max(right - left, MIN_WINDOW_PX)
        g.color = WINDOW_OVERLAY_FILL
        g.fillRect(left, 0, windowWidth, height)
        g.color = windowOverlayBorderColor
        g.drawRect(left, 0, windowWidth - 1, height - 1)
        g.font = windowLabelFont
        val fm = g.getFontMetrics(windowLabelFont)
        g.color = UIManager.getColor("Label.foreground") ?: WINDOW_LABEL_FALLBACK_COLOR
        g.drawString(range.label, LABEL_PADDING, (height + fm.ascent - fm.descent) / 2)
    }

    fun clear() {
        image = null
        windowRange = null
        repaint()
    }

    companion object {
        internal val windowOverlayBorderColor = Color(120, 170, 255)
    }
}
