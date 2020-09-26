package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.Main.SETTINGS_THREAD_TIME_MODE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

interface View {
    var levelHeight: Double
    var bounds: Rectangle2D.Double
    var fontName: String
    fun redraw()
    fun drawRect(
        g: Graphics2D, fm: FontMetrics,
        rect: FlameRectangle, topOffset: Double,
        borderColor: Color, fillColor: Color
    )

    fun drawRect(g: Graphics2D, rect: Rectangle2D, color: Color) = Unit

    fun fitZoom(rect: Rectangle2D.Double)
    fun showDialog()
    fun hideDialog()
}

class FlameChartController(
    private val methodsColor: MethodsColor,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) {
    var view: View? = null
        set(value) {
            field = value
            value?.let {
                initView(it)
            }
        }

    private var currentSelectedElement: FlameRectangle? = null
    private val rectangles = mutableListOf<FlameRectangle>()
    private val edgesColor = Color(0, 0, 0, 131)
    private val defaultFillColor = Color(0xD7AE65)
    private val selectedElementFillColor = Color(115, 238, 46);
    private val selectedElementEdgesColor = Color(0xE2CF95)
    private var topOffset = 0.0
    private var selectedElements: List<ProfileDataImpl> = emptyList()
    var isViewVisible = false
        private set

    private fun initView(view: View) {
        view.fontName = settings.getStringValueOrDefault(ProfilerPanel.SETTINGS_FONT_NAME, "Arial")
        settings.setStringValue(ProfilerPanel.SETTINGS_FONT_NAME, view.fontName)
        val cellFontSize =
            settings.getIntValueOrDefault(ProfilerPanel.SETTINGS_CELL_FONT_SIZE, ProfilerPanel.DEFAULT_CELL_FONT_SIZE)
        view.levelHeight = cellFontSize + 3.0
    }

    /**
     * Switches between thread / global time.
     */
    fun switchTimeMode(isThreadTime: Boolean) {
        showFlameChart(selectedElements)
    }

    internal fun onDialogClosed() {
        isViewVisible = false
    }

    fun showFlameChart(selectedElements: ProfileDataImpl) {
        showFlameChart(listOf(selectedElements))
    }

    fun showFlameChart(selectedElements: List<ProfileDataImpl>) {
        this.selectedElements = selectedElements
        coroutineScope.launch(dispatchers.ui) {
            val levelHeight = view?.levelHeight ?: 1.0
            val data = calculateFlame(levelHeight, selectedElements)
            rectangles.clear()
            rectangles.addAll(data.rectangles)
            val bounds =
                Rectangle2D.Double(
                    data.minLeftOffset,
                    0.0,
                    data.maxRightOffset - data.minLeftOffset,
                    data.topOffset + levelHeight
                )
            view?.bounds = bounds
            view?.fitZoom(bounds)

            view?.redraw()
            view?.showDialog()
        }
    }

    private suspend fun calculateFlame(levelHeight: Double, selectedElements: List<ProfileDataImpl>): Result {
        val data = coroutineScope.async(dispatchers.worker) {
            val calculator = FlameCalculator(
                methodsColor,
                settings.getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE),
                levelHeight
            )
            val rootSource = selectedElements.first()
            return@async calculator.calculateFlame(rootSource)
        }
        return data.await()
    }

    private class FlameCalculator(
        private val methodsColor: MethodsColor,
        private val isThreadTime: Boolean,
        private val levelHeight: Double
    ) {
        private var rootLevel: Int = 0
        private val result = mutableListOf<FlameRectangle>()
        var topOffset = 0.0
            private set

        fun calculateFlame(rootSource: ProfileDataImpl): Result {
            result.clear()
            rootLevel = rootSource.level

            val top = calculateTopForLevel(rootSource)
            val left = calculateStartXForTime(rootSource)
            val right = calculateEndXForTime(rootSource)
            val width = right - left

            val root = FlameRectangle(left, top, width, levelHeight, rootSource.name, 1)
            result.add(root)

            processChildren(listOf(rootSource), left)
            var maxX = Double.MIN_VALUE
            var minX = Double.MAX_VALUE

            for (rect in result) {
                rect.y -= topOffset
                if (rect.maxX > maxX) {
                    maxX = rect.maxX
                }
                if (rect.minX < minX) {
                    minX = rect.minX
                }
                rect.color = methodsColor.getColorForMethod(rect.name)
            }
            return Result(result, minX, -topOffset, maxX)
        }

        private fun processChildren(rootSources: List<ProfileDataImpl>, parentLeft: Double) {
            val children = mutableMapOf<String, ChildHolder>()
            var left = parentLeft
            for (root in rootSources) {
                for (child in root.children) {
                    val childHolder = children.getOrPut(child.name, { ChildHolder() })
                    val left = calculateStartXForTime(child)
                    val right = calculateEndXForTime(child)
                    val width = right - left
                    childHolder.totalDuration += width
                    childHolder.children.add(child)
                }
            }

            val sorted = children.toList().sortedByDescending { (_, value) -> value.totalDuration }.toMap()

            for (entry in sorted) {
                val top = calculateTopForLevel(entry.value.children.first())
                result.add(
                    FlameRectangle(
                        left,
                        top,
                        entry.value.totalDuration,
                        levelHeight, entry.key,
                        entry.value.children.size
                    )
                )
                processChildren(entry.value.children, left)
                left += entry.value.totalDuration
            }
        }

        private fun calculateStartXForTime(record: ProfileDataImpl): Double {
            return if (isThreadTime) {
                record.threadStartTimeInMillisecond
            } else {
                record.globalStartTimeInMillisecond
            }
        }

        private fun calculateEndXForTime(record: ProfileDataImpl): Double {
            return if (isThreadTime) {
                record.threadEndTimeInMillisecond
            } else {
                record.globalEndTimeInMillisecond
            }
        }

        private fun calculateTopForLevel(record: ProfileDataImpl): Double {
            val top = (rootLevel - record.level) * levelHeight
            if (top < topOffset) {
                topOffset = top
            }
            return top
        }
    }

    fun onDraw(
        g: Graphics2D,
        fm: FontMetrics,
        minimumSizeInMs: Double,
        screenLeft: Double,
        screenTop: Double,
        screenRight: Double,
        screenBottom: Double
    ) {
        view?.let { v ->
            for (element in rectangles) {
                if (!element.isInScreen(screenLeft, screenTop, screenRight, screenBottom)) {
                    continue
                }
                if (element.width < minimumSizeInMs) {
                    continue
                }
                var fillColor = element.color ?: defaultFillColor
                var borderColor = edgesColor
                if (currentSelectedElement == element) {
                    fillColor = selectedElementFillColor
                    borderColor = selectedElementEdgesColor
                }
                v.drawRect(g, fm, element, topOffset, borderColor, fillColor)
            }
        }
    }

    fun showDialog() {
        view?.let {
            it.showDialog()
            isViewVisible = true
        }
    }

    fun findDataByPosition(transformedPoint: Point2D.Float): FlameRectangle? {
        for (rectangle in rectangles) {
            if (rectangle.isInside(transformedPoint.getX(), transformedPoint.getY())) {
                return rectangle
            }
        }
        return null
    }

    fun selectElement(selectedData: FlameRectangle) {
        currentSelectedElement = selectedData
        view?.redraw()
    }

    fun removeSelection() {
        currentSelectedElement = null
        view?.redraw()
    }

    data class ChildHolder(
        var totalDuration: Double = 0.0,
        val children: MutableList<ProfileDataImpl> = mutableListOf()
    )

    private data class Result(
        val rectangles: List<FlameRectangle>,
        val minLeftOffset: Double,
        val topOffset: Double,
        val maxRightOffset: Double
    )
}
