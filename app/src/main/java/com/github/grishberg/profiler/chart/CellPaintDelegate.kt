package com.github.grishberg.profiler.chart

import java.awt.FontMetrics
import java.awt.Graphics2D

interface CellPaintDelegate {
    fun drawLabel(
        g: Graphics2D,
        fm: FontMetrics,
        name: String,
        left: Double, right: Double, top: Int
    )
}
