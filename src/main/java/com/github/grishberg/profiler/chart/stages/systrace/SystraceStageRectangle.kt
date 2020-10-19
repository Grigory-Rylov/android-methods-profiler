package com.github.grishberg.profiler.chart.stages.systrace

import java.awt.Color
import java.awt.geom.Rectangle2D

class SystraceStageRectangle(
    val name: String,
    val color: Color
) : Rectangle2D.Double() {
    val headerTitleColor = Color.WHITE
}
