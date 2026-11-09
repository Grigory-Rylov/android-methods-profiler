package com.github.grishberg.profiler.chart.racing

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import kotlin.math.ceil

/** Итог LOD-прохода сетки; [minorStep] = null, когда minor-линии не рисуем. */
data class GridSpec(val step: Double, val minorStep: Double?, val majorLineCount: Int)

/** Отрисовка LOD-сетки только по видимой области. */
object GridPainter {
    /**
     * Порог «растянутости» major-линии, при которой между ними дорисовываются minor.
     * При minPx=70 шаг major всегда ≥70px, поэтому minor-интервал (70/minorDiv) и при
     * среднем зуме не мелче 14px — планка «12px» вырождается. Реальный LOD-переход
     * («при среднем зуме minor нет, при сильном — появляются») едет по major-интервалу:
     * minor включаются, только когда major растянулись минимум до 120px.
     */
    const val MINOR_TRIGGER_PX = 120.0

    /**
     * Единое LOD-правило minor-сетки: шаг minor, если major растянуты минимум до
     * [MINOR_TRIGGER_PX]; иначе null. Его используют GridPainter.paint и TimelineHeader —
     * правило не разъезжается.
     */
    fun minorStepFor(pxPerUnit: Double): Double? {
        val step = GridMath.niceStep(pxPerUnit)
        return if (step * pxPerUnit >= MINOR_TRIGGER_PX) step / GridMath.minorDivisions(step) else null
    }

    /**
     * Сетка: X-линии по [ViewState.scale], Y-линии по [ViewState.scaleY] со своим
     * LOD — в анизотропном режиме оси живут раздельно. [GridSpec] — параметры X-оси.
     */
    fun paint(g: Graphics2D, state: ViewState, width: Int, height: Int, colors: RacingColorScheme): GridSpec {
        val px = state.scale
        val py = state.scaleY
        val step = GridMath.niceStep(px)
        val minorStep = minorStepFor(px)
        val stepY = if (state.verticalZoomEnabled) step else GridMath.niceStep(py)
        val minorStepY = if (state.verticalZoomEnabled) minorStep else minorStepFor(py)
        val oldStroke = g.stroke

        g.color = colors.gridMinor
        g.stroke = BasicStroke(1f)
        if (minorStep != null) paintVLines(g, state, px, minorStep, width, height)
        if (minorStepY != null) paintHLines(g, state, py, minorStepY, width, height)

        g.color = colors.gridMajor
        g.stroke = BasicStroke(1f)
        val majors = paintVLines(g, state, px, step, width, height)
        paintHLines(g, state, py, stepY, width, height)

        g.color = colors.gridAccent
        g.stroke = BasicStroke(1.5f)
        val sx = ((0.0 - state.viewX) * px + 0.5).toInt()
        if (sx in 0 until width) g.drawLine(sx, 0, sx, height)
        val sy = ((0.0 - state.viewY) * py + 0.5).toInt()
        if (sy in 0 until height) g.drawLine(0, sy, width, sy)
        g.stroke = oldStroke

        return GridSpec(step, minorStep, majors)
    }

    /** Вертикальные линии (мировой X кратно step). Возвращает число проведённых. */
    private fun paintVLines(g: Graphics2D, state: ViewState, px: Double, step: Double, width: Int, height: Int): Int {
        var count = 0
        val x1 = state.viewX + width / px
        var k = ceil(state.viewX / step)
        while (k * step <= x1) {
            val sx = ((k * step - state.viewX) * px + 0.5).toInt()
            g.drawLine(sx, 0, sx, height)
            count++
            k++
        }
        return count
    }

    /** Горизонтальные линии (мировой Y кратно step). */
    private fun paintHLines(g: Graphics2D, state: ViewState, py: Double, step: Double, width: Int, height: Int) {
        val y1 = state.viewY + height / py
        var m = ceil(state.viewY / step)
        while (m * step <= y1) {
            val sy = ((m * step - state.viewY) * py + 0.5).toInt()
            g.drawLine(0, sy, width, sy)
            m++
        }
    }
}
