package com.github.grishberg.profiler.chart.flame

import java.awt.Color
import java.awt.geom.Rectangle2D


class FlameRectangle(
    startThreadTime: kotlin.Double,
    y: kotlin.Double,
    duration: kotlin.Double,
    h: kotlin.Double,
    val name: String,
    val count: Int,
    var color: Color? = null
) : Rectangle2D.Double(startThreadTime, y, duration, h) {
    var isFoundElement = false
    fun isInside(cx: kotlin.Double, cy: kotlin.Double): Boolean {
        return cx >= x && cx <= x + width && cy >= y && cy <= y + height
    }

    fun isInScreen(
        screenLeft: kotlin.Double,
        screenTop: kotlin.Double,
        screenRight: kotlin.Double,
        screenBottom: kotlin.Double
    ): Boolean {
        if (x < screenLeft && x + width < screenLeft) {
            return false
        }
        if (x > screenRight && x + width > screenRight) {
            return false
        }
        if (y < screenTop && y + height < screenTop) {
            return false
        }
        return !(y > screenBottom && y + height > screenBottom)
    }

    override fun toString(): String {
        return "FlameRectangle{" +
                "name=" + name +
                ", x=" + x +
                ", y=" + y +
                ", w=" + width +
                '}'
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is FlameRectangle) return false
        return name == obj.name && super.equals(obj)
    }
}
