package com.github.grishberg.profiler.chart.racing

import kotlin.math.exp

/**
 * Семантика колеса мыши для вьюпорта трейса (чистая логика, без Swing):
 *
 * * Shift + колесо — зум, заякоренный под курсором;
 * * Ctrl + колесо — зум (pinch-эхо на macOS фильтруется на уровне панели);
 * * колесо без модификаторов — горизонтальный скролл (канон трейс-вьюеров).
 *
 * Колесо вверх (rotation < 0) увеличивает масштаб и скроллит влево.
 */
sealed class WheelGesture {
    class Scroll(val dxPx: Double) : WheelGesture()
    class Zoom(val factor: Double) : WheelGesture()
    object Ignored : WheelGesture()

    companion object {
        const val SCROLL_PX_PER_ROTATION = 60.0
        const val ZOOM_EXP = 0.25
        const val PINCH_ZOOM_EXP = 0.18

        fun isZoom(isShiftDown: Boolean): Boolean = isShiftDown

        fun classify(isShiftDown: Boolean, isCtrlDown: Boolean, rotation: Double): WheelGesture {
            if (isShiftDown || isCtrlDown) {
                val exp = if (isShiftDown) ZOOM_EXP else PINCH_ZOOM_EXP
                return Zoom(exp(-rotation * exp))
            }
            if (rotation == 0.0) return Ignored
            return Scroll(rotation * SCROLL_PX_PER_ROTATION)
        }
    }
}
