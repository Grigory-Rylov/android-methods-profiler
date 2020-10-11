package com.github.grishberg.profiler.chart.stages.systrace

import com.github.grishberg.profiler.common.contrastColor
import java.awt.Color
import java.awt.geom.Rectangle2D

class SystraceStageRectangle(
    val name: String,
    val color: Color
) : Rectangle2D.Double() {
    var headerTitleColor = contrastColor(color)
        private set
}
