package com.github.grishberg.profiler.chart.stages.systrace

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.chart.ChartPaintDelegate
import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.chart.RepaintDelegate
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.tracerecorder.SystraceRecord
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import kotlin.math.max

class SystraceStagesFacade(
    private val log: AppLogger
) {
    private val colors = listOf(
        Color(0x7678ed),
        Color(0xf7b801),
        Color(0xf18701),
        Color(0xf35b04),
        Color(0x4DC5E5),
        Color(0x8c6057),
        Color(0xbf4342),
        Color(0xa1c181),
        Color(0x80C937)
    )
    private val randomColors = colors.shuffled()
    private var nextColorIndex = 0

    private val stagesRectangles = mutableListOf<SystraceStageRectangle>()

    var repaintDelegate: RepaintDelegate? = null
    var labelPaintDelegate: ChartPaintDelegate? = null
    var height = -1.0
    var shouldShow: Boolean = true

    fun setSystraceStages(
        trace: AnalyzerResult,
        systraceRecords: List<SystraceRecord>
    ) {
        val maxGlobalTime = calculateMaxGlobalTime(trace)
        stagesRectangles.clear()
        var previousSystraceRecord: SystraceRecord? = null
        for (systrace in systraceRecords) {
            if (previousSystraceRecord == null) {
                previousSystraceRecord = systrace
                continue
            }
            val element = SystraceStageRectangle(previousSystraceRecord.name, getNextColor())
            val start = previousSystraceRecord.startTime * 1000 - trace.startTimeUs / 1000
            val end = systrace.startTime * 1000 - trace.startTimeUs / 1000
            element.x = start
            element.width = end - start
            stagesRectangles.add(element)
            previousSystraceRecord = systrace
        }
        if (previousSystraceRecord != null) {
            val element = SystraceStageRectangle(previousSystraceRecord.name, getNextColor())
            val start = previousSystraceRecord.startTime * 1000 - trace.startTimeUs / 1000
            val end = maxGlobalTime
            element.x = start
            element.width = end - start
            stagesRectangles.add(element)
        }
        repaintDelegate?.repaint()
    }

    private fun getNextColor(): Color {
        val selectedColor = randomColors[nextColorIndex++]
        if (nextColorIndex >= randomColors.size) {
            nextColorIndex = 0
        }
        return Color(selectedColor.red, selectedColor.green, selectedColor.blue, 180)
    }

    private fun calculateMaxGlobalTime(trace: AnalyzerResult): Double {
        var maxGlobalTime = 0.0
        for (bound in trace.globalTimeBounds) {
            maxGlobalTime = max(maxGlobalTime, bound.value.maxTime)
        }
        return maxGlobalTime
    }

    fun onThreadModeSwitched(threadTime: Boolean) {
        shouldShow = !threadTime
    }

    fun drawStages(g: Graphics2D, at: AffineTransform, fm: FontMetrics) {
        if (!shouldShow) {
            return
        }
        for (stageRectangle in stagesRectangles) {
            val transformedShape = at.createTransformedShape(stageRectangle)
            val rect = transformedShape.bounds
            val name = stageRectangle.name

            val cx = rect.x + rect.width / 2
            val labelTextWidth = max(fm.stringWidth(name), ProfilerPanel.MARKER_LABEL_TEXT_MIN_WIDTH)

            // background
            g.color = stageRectangle.color
            g.fillRect(rect.x, 0, rect.width, ProfilerPanel.TOP_OFFSET)

            g.color = stageRectangle.headerTitleColor
            if (name.isNotEmpty()) {
                if (labelTextWidth <= rect.width) {
                    g.drawString(name, cx - labelTextWidth / 2, fm.height)
                } else {
                    labelPaintDelegate?.drawLabel(g, fm, name, rect, fm.height)
                }
            }
        }
    }
}
