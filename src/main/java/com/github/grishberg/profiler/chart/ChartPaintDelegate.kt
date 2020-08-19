package com.github.grishberg.profiler.chart

import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Rectangle

interface ChartPaintDelegate {
    fun drawLabel(
        g: Graphics2D,
        fm: FontMetrics,
        name: String,
        horizontalBounds: Rectangle,
        topPosition: Int
    )
}