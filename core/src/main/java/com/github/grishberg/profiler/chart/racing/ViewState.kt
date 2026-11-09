package com.github.grishberg.profiler.chart.racing

import kotlin.math.max
import kotlin.math.min

/**
 * Состояние вьюпорта + математика клампов. Чистый класс, без Swing.
 *
 * screenX = (worldX - viewX) * scale;  worldX = viewX + screenX / scale.
 * scale — пикселей на мировую единицу. По Y — [scaleY]: тот же scale (изотропно)
 * или фиксированный [yScale] при [verticalZoomEnabled]=false (анизотропно).
 *
 * Мировая ось X = миллисекунды трейса, сдвинутые на [timeOffset] относительно
 * времени записи (сдвиг nonzero, только когда у треда есть вызовы с отрицательным
 * временем старта; заголовок таймлайна вычитает offset из подписей тиков).
 */
class ViewState(
    var viewX: Double = 0.0,
    var viewY: Double = 0.0,
    var scale: Double = DEFAULT_SCALE,
    var worldWidth: Double = 2400.0,
    var worldHeight: Double = 960.0,
    /** Реальное время = мировое X минус этот offset. Только для подписей таймлайна. */
    var timeOffset: Double = 0.0,
) {
    /** false → зум не масштабирует Y: высота строк фиксирована от шрифта. */
    var verticalZoomEnabled: Boolean = true

    /** Пикселей на мировую единицу по Y, когда verticalZoomEnabled=false. */
    var yScale: Double = 1.0

    /** Фактический масштаб по Y. */
    val scaleY: Double get() = if (verticalZoomEnabled) scale else yScale

    fun worldXUnder(screenX: Double): Double = viewX + screenX / scale
    fun worldYUnder(screenY: Double): Double = viewY + screenY / scaleY

    /** viewX ∈ [0, max(0, worldWidth - vpW/scale)]; если контент влезает — 0. */
    fun clamp(viewportW: Double, viewportH: Double) {
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        viewX = if (worldWidth * scale <= viewportW) 0.0
        else viewX.coerceIn(0.0, max(0.0, worldWidth - viewportW / scale))
        viewY = if (worldHeight * scaleY <= viewportH) 0.0
        else viewY.coerceIn(0.0, max(0.0, worldHeight - viewportH / scaleY))
    }

    /**
     * Нижний край зума: масштаб, при котором контент целиком влезает в вьюпорт
     * (скроллбары пропали). В анизотропном режиме Y зумом не масштабируется —
     * пол задаёт только ось X.
     */
    fun minScaleToFit(viewportW: Double, viewportH: Double): Double {
        val fitX = viewportW / worldWidth
        val fit = if (verticalZoomEnabled) minOf(fitX, viewportH / worldHeight) else fitX
        return fit.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** Зум с множителем factor, заякоренный в мировой точке под курсором. */
    fun zoomAt(screenX: Double, screenY: Double, factor: Double, viewportW: Double, viewportH: Double) {
        val wx = worldXUnder(screenX)
        val wy = worldYUnder(screenY)
        val target = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        // Зум-аут останавливается на «всё влезло»: ниже minScaleToFit не уходим,
        // а если уже влезло (в т.ч. из-за роста окна) — scale не двигаем вовсе,
        // чтобы отзум не превращался в отзум-в-обратную-сторону.
        scale = if (target < scale) max(target, min(scale, minScaleToFit(viewportW, viewportH))) else target
        viewX = wx - screenX / scale
        viewY = wy - screenY / scaleY
        clamp(viewportW, viewportH)
    }

    /** Панора на dxPx/dyPx экранных пикселей. */
    fun scrollBy(dxPx: Double, dyPx: Double, viewportW: Double, viewportH: Double) {
        viewX += dxPx / scale
        viewY += dyPx / scaleY
        clamp(viewportW, viewportH)
    }

    /** Вписать отрезок [fromX, toX] по X в вьюпорт; Y не трогаем. */
    fun focusX(fromX: Double, toX: Double, viewportW: Double) {
        val span = toX - fromX
        require(span > 0.0 && span.isFinite()) { "span must be finite and > 0, got $span" }
        scale = (viewportW / span).coerceIn(MIN_SCALE, MAX_SCALE)
        viewX = fromX
    }

    fun fit(viewportW: Double, viewportH: Double) {
        scale = minScaleToFit(viewportW, viewportH)
        viewX = 0.0
        viewY = 0.0
        clamp(viewportW, viewportH)
    }

    /** Скроллбар по оси нужен ровно когда контент не влезает. */
    fun needsHScroll(viewportW: Double): Boolean = worldWidth * scale > viewportW + 0.5
    fun needsVScroll(viewportH: Double): Boolean = worldHeight * scaleY > viewportH + 0.5

    companion object {
        const val MIN_SCALE = 0.02
        const val MAX_SCALE = 50.0
        const val DEFAULT_SCALE = 1.0
    }
}
