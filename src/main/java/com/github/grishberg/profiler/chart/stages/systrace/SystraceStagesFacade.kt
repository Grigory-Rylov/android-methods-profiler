package com.github.grishberg.profiler.chart.stages.systrace

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.analyzer.calculateMaxGlobalTime
import com.github.grishberg.profiler.chart.ChartPaintDelegate
import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.chart.RepaintDelegate
import com.github.grishberg.profiler.chart.stages.MethodsListIterator
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import com.github.grishberg.profiler.plugins.stages.StagesFactory
import com.github.grishberg.profiler.plugins.stages.systrace.SystraceStagesFactory
import com.github.grishberg.tracerecorder.SystraceRecord
import com.github.grishberg.tracerecorder.SystraceRecordResult
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
    private val innerRecordsList = mutableListOf<SystraceRecord>()
    val recordsList: List<SystraceRecord>
        get() = innerRecordsList

    var repaintDelegate: RepaintDelegate? = null
    var labelPaintDelegate: ChartPaintDelegate? = null
    var height = -1.0
    var shouldShow: Boolean = true
    private var methodsRectangles: List<ProfileRectangle>? = null

    fun setSystraceStages(
        trace: AnalyzerResult,
        systrace: SystraceRecordResult
    ) {
        innerRecordsList.clear()
        val tracesOffset = trace.startTimeUs / 1000
        val offset = systrace.startOffset * 1000 + (tracesOffset - systrace.parentTs * 1000)
        for (record in systrace.records) {
            innerRecordsList.add(
                SystraceRecord(
                    record.name, record.cpu,
                    record.startTime * 1000 - offset,
                    record.endTime * 1000 - offset
                )
            )
        }

        val maxGlobalTime = trace.calculateMaxGlobalTime()
        stagesRectangles.clear()
        var previousSystraceRecord: SystraceRecord? = null
        for (systrace in systrace.records) {

            if (previousSystraceRecord == null) {
                previousSystraceRecord = systrace
                continue
            }
            val element = SystraceStageRectangle(previousSystraceRecord.name, getNextColor())
            val start = previousSystraceRecord.startTime * 1000 - offset
            val end = systrace.startTime * 1000 - offset
            element.x = start
            element.width = end - start
            stagesRectangles.add(element)
            previousSystraceRecord = systrace
        }
        if (previousSystraceRecord != null) {
            val element = SystraceStageRectangle(previousSystraceRecord.name, getNextColor())
            val start = previousSystraceRecord.startTime * 1000 - offset
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

    /**
     * Should be called after new trace was opened.
     */
    fun onThreadSwitched(
        rectangles: List<ProfileRectangle>,
        isMainThread: Boolean,
        threadTime: Boolean,
        toolbarHeight: Double
    ) {
        shouldShow = !threadTime
        if (isMainThread) {
            methodsRectangles = rectangles
            return
        }
        methodsRectangles = null
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

    fun getStagesFactory(methodsAvailability: MethodsAvailability): StagesFactory {
        return SystraceStagesFactory(
            { MethodsListIterator(methodsRectangles!!) },
            recordsList,
            methodsAvailability,
            log
        )
    }
}
