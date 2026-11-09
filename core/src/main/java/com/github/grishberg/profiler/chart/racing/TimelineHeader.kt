package com.github.grishberg.profiler.chart.racing

import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.Locale
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Временная шкала над панелью: тики на тех же кратных niceStep, что и major
 * линии сетки; подписи в адаптивных единицах (мировая единица по X = миллисекунда).
 * Подписи показывают реальное время трейса: мировое X минус [ViewState.timeOffset].
 */
class TimelineHeader(
    private val state: ViewState,
    /** Общая схема; VirtualSpaceView подменяет её вместе с панелью. */
    var colors: RacingColorScheme,
) : JComponent() {

    override fun paintComponent(g0: Graphics) {
        val g = g0 as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = colors.headerBackground
        g.fillRect(0, 0, width, height)

        val px = state.scale
        val step = GridMath.niceStep(px)
        val minorStep = GridPainter.minorStepFor(px)
        g.font = FONT
        val fm = g.fontMetrics
        val baseline = height - 15
        if (minorStep != null) {
            g.color = colors.headerMinorTick
            var mk = ceil(state.viewX / minorStep).toLong()
            val minorX1 = state.viewX + width / px
            while (mk * minorStep <= minorX1) {
                val t = mk * minorStep
                if (abs(t / step - ceil(t / step)) > 1e-9) { // не совпадает с major
                    val sx = ((t - state.viewX) * px + 0.5).toInt()
                    g.drawLine(sx, height - 6, sx, height - 1)
                }
                mk++
            }
        }

        g.color = colors.headerTick
        g.drawLine(0, height - 1, width, height - 1)

        var k = ceil(state.viewX / step).toLong()
        val x1w = state.viewX + width / px
        while (k * step <= x1w) {
            val t = k * step
            val sx = ((t - state.viewX) * px + 0.5).toInt()
            g.color = colors.headerTick
            g.drawLine(sx, height - 12, sx, height - 1)
            val label = formatTick(t - state.timeOffset, step)
            val tw = fm.stringWidth(label)
            if (sx + 4 + tw <= width) {
                g.color = colors.headerText
                g.drawString(label, sx + 4, baseline)
            }
            k++
        }
    }

    private fun formatTick(t: Double, step: Double): String = when {
        step >= 1000.0 -> trim(t / 1000.0) + " s"
        step >= 1.0 -> trim(t) + " ms"
        else -> String.format(Locale.US, "%.1f µs", t * 1000.0)
    }

    private fun trim(v: Double): String =
        if (abs(v - Math.round(v)) < 1e-6) Math.round(v).toString() else String.format(Locale.US, "%.3f", v)

    companion object {
        const val HEIGHT = 44
        private val FONT = Font(Font.SANS_SERIF, Font.PLAIN, 11)
    }
}
