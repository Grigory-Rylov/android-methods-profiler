package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.TimeFormatter
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.util.ArrayList

private const val AXISES_COUNT = 10
private const val MINIMUM_DURATION = 1.0 / 1000000000.0

/**
 * Draws vertical line grid.
 */
class Grid(
    private val settings: SettingsRepository,
    private val topOffset: Int,
    private val timeFormatter: TimeFormatter,
    private val labelFont: Font,
    private val labelFontMetrics: FontMetrics
) {
    private val labelColor = Color(246, 255, 241)
    private val lineColor = Color(191, 198, 187, 120)

    var screenWidth: Int = 0
        set(value) {
            field = value
            maxScreenRange = value * k
            minScreenRange = value / k

        }
    var enabled = false

    private val scaleLines = ArrayList<Line2D.Double>()
    private var firstLineX: Double = 0.0
    private var distance: Double = 200.0
    private val k: Double = 2.0
    private var maxScreenRange: Double = 0.0
    private var minScreenRange: Double = 0.0

    init {
        enabled = settings.getBoolValueOrDefault(Main.SETTINGS_GRID, true)
        var x = 0.0
        for (i in 0..10) {
            scaleLines.add(Line2D.Double(x, (-topOffset).toDouble(), x, 1000.0))
            x += distance
        }
    }

    fun draw(
        g: Graphics2D,
        at: AffineTransform,
        screenLeft: Double,
        screenTop: Double,
        screenRight: Double,
        screenBottom: Double
    ) {
        if (!enabled || screenWidth == 0) {
            return
        }
        val transformedScreenWidth = screenRight - screenLeft
        while (transformedScreenWidth < minScreenRange) {
            if (distance / k < MINIMUM_DURATION) {
                return
            }
            minScreenRange /= k
            maxScreenRange /= k
            distance /= k
        }
        while (transformedScreenWidth > maxScreenRange) {
            minScreenRange *= k
            maxScreenRange *= k
            distance *= k
        }

        if (distance < MINIMUM_DURATION) {
            return
        }
        while (distance > 0 && firstLineX > screenLeft && firstLineX - distance > screenLeft) {
            firstLineX -= distance
        }

        while (distance > 0 && firstLineX < screenLeft && firstLineX - distance < screenLeft) {
            firstLineX += distance
        }
        var x: Double = firstLineX

        var n = 0
        for (line in scaleLines) {
            line.x1 = x
            line.x2 = x
            line.y1 = -topOffset.toDouble()
            line.y2 = screenBottom

            if (x > screenRight) {
                break
            }

            val transformedShape = at.createTransformedShape(line)
            g.color = lineColor
            g.draw(transformedShape)

            val timeAsString = timeFormatter.timeToString(x)
            val labelWidth: Int = labelFontMetrics.stringWidth(timeAsString)
            g.color = labelColor
            g.font = labelFont
            g.drawString(timeAsString, transformedShape.bounds.x - labelWidth / 2, labelFontMetrics.height)

            n++

            x += distance
        }
    }
}
