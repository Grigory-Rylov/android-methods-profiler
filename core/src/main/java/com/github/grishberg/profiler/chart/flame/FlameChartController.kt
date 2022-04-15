package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.ElementColor
import com.github.grishberg.profiler.chart.FoundInfoListener
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.darker
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.comparator.AggregatedFlameProfileData
import com.github.grishberg.profiler.ui.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.min

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
    fun requestFocus()
}

interface DialogView {
    fun hideInfoPanel()
    fun exitFromSearching()
}

private const val NOT_FOUND_ITEM_DARKEN_FACTOR = 0.5

class FlameChartController(
    private val methodsColor: MethodsColor,
    private val settings: SettingsFacade,
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
    var foundInfoListener: FoundInfoListener<FlameRectangle>? = null
    var dialogView: DialogView? = null

    private val textUtils = TextUtils()

    private val colorBuffer = ElementColor()
    private var isSearchingInProgress = false
    private val foundItems = ArrayList<FlameRectangle>()
    private var currentFocusedFoundElement: Int = -1

    private var currentSelectedElement: FlameRectangle? = null
    private val rectangles = mutableListOf<FlameRectangle>()
    private val edgesColor = Color(0, 0, 0, 131)
    private val defaultFillColor = Color(0xD7AE65)
    private val selectedElementFillColor = Color(115, 238, 46);
    private val selectedElementEdgesColor = Color(0xE2CF95)

    private val selectedBookmarkBorderColor = Color(246, 255, 241)
    private val selectionColor = Color(115, 238, 46)
    private val foundColor = Color(70, 238, 220)
    private val focusedFoundColor = Color(171, 238, 221)
    private val selectedFoundColor = Color(110, 238, 161)

    private var topOffset = 0.0
    private var selectedElements: List<ProfileData> = emptyList()
    var isViewVisible = false
        private set

    private fun initView(view: View) {
        view.fontName = settings.fontName
        val cellFontSize = settings.fontSize
        view.levelHeight = cellFontSize + 3.0
    }

    /**
     * Switches between thread / global time.
     */
    fun switchTimeMode(isThreadTime: Boolean) {
        showFlameChart(selectedElements, isThreadTime)
    }

    internal fun onDialogClosed() {
        isViewVisible = false
    }

    fun showFlameChart(selectedElements: ProfileData, isThreadTime: Boolean) {
        showFlameChart(listOf(selectedElements), isThreadTime)
    }

    fun showFlameChart(selectedElements: List<ProfileData>, isThreadTime: Boolean) {
        this.selectedElements = selectedElements
        coroutineScope.launch(dispatchers.ui) {
            val levelHeight = view?.levelHeight ?: 1.0
            val data = calculateFlame(levelHeight, selectedElements, isThreadTime)
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

    fun showAggregatedFlameChart(aggregatedChart: AggregatedFlameProfileData, topOffset: Double) {
        rectangles.clear()
        val result = aggregatedChart.toRectangles()
        rectangles.addAll(result.rectangles)
        val bounds =
            Rectangle2D.Double(
                result.minLeftOffset,
                0.0,
                result.maxRightOffset - result.minLeftOffset,
                result.topOffset + 15.0
            )
        view?.bounds = bounds
        view?.fitZoom(bounds)

        view?.redraw()
        view?.showDialog()
    }

    private fun AggregatedFlameProfileData.toRectangles(): Result {
        val result = mutableListOf<FlameRectangle>()
        for (child in children) {
            toRectangle(child, result)
        }
        val topOffset = children.minOf { it.top }
        for (child in result) {
            child.y -= topOffset
            child.color = methodsColor.getColorForMethod(child.name)
        }
        return Result(result, left, -topOffset, left + width)
    }

    private fun toRectangle(node: AggregatedFlameProfileData, fillIn: MutableList<FlameRectangle>) {
        fillIn.add(
            FlameRectangle(
                node.left,
                node.top,
                node.width,
                15.0,
                node.name,
                node.mean.toInt()  // TODO: allow to show double?
            )
        )
        for (child in node.children) {
            toRectangle(child, fillIn)
        }
    }

    private suspend fun calculateFlame(
        levelHeight: Double,
        selectedElements: List<ProfileData>,
        isThreadTime: Boolean
    ): Result {
        val data = coroutineScope.async(dispatchers.worker) {
            val calculator = FlameCalculator(
                methodsColor,
                isThreadTime,
                levelHeight
            )
            if (selectedElements.size == 1) {
                return@async calculator.calculateFlame(selectedElements.first())
            }
            return@async calculator.calculateFlame(selectedElements)
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

        fun calculateFlame(threadMethods: List<ProfileData>): Result {
            result.clear()
            rootLevel = threadMethods.first().level
            val rootsSource = threadMethods.filter { it.level == rootLevel }

            var left = 0.0

            for (rootSource in rootsSource) {
                val currenLeft = calculateStartXForTime(rootSource)
                left = min(left, currenLeft)
            }

            processChildren(rootsSource, left)

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
            return Result(result, minX, -topOffset - levelHeight, maxX)
        }

        fun calculateFlame(rootSource: ProfileData): Result {
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

        private fun processChildren(rootSources: List<ProfileData>, parentLeft: Double) {
            val children = mutableMapOf<String, ChildHolder>()
            var left = parentLeft
            for (root in rootSources) {
                for (child in root.children) {
                    val childHolder = children.getOrPut(child.name) { ChildHolder() }
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

        private fun calculateStartXForTime(record: ProfileData): Double {
            return if (isThreadTime) {
                record.threadStartTimeInMillisecond
            } else {
                record.globalStartTimeInMillisecond
            }
        }

        private fun calculateEndXForTime(record: ProfileData): Double {
            return if (isThreadTime) {
                record.threadEndTimeInMillisecond
            } else {
                record.globalEndTimeInMillisecond
            }
        }

        private fun calculateTopForLevel(record: ProfileData): Double {
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
                val color = calculateColor(element)
                v.drawRect(g, fm, element, topOffset, color.borderColor, color.fillColor)
            }
        }
    }

    private fun calculateColor(element: FlameRectangle): ElementColor {
        val isSelectedElement = element == currentSelectedElement

        if (isSearchingInProgress) {
            if (element.isFoundElement) {
                val isFocusedElement =
                    currentFocusedFoundElement >= 0 && element === foundItems[currentFocusedFoundElement]
                var color: Color = if (isSelectedElement) selectedFoundColor else foundColor
                if (isFocusedElement && !isSelectedElement) {
                    color = focusedFoundColor
                }
                val borderColor = if (isFocusedElement) selectedBookmarkBorderColor else edgesColor
                colorBuffer.set(color, borderColor)
                return colorBuffer
            } else {
                if (isSelectedElement) {
                    colorBuffer.set(selectionColor, edgesColor)
                }
                val color: Color = element.color ?: defaultFillColor
                colorBuffer.set(color.darker(NOT_FOUND_ITEM_DARKEN_FACTOR), edgesColor)
                return colorBuffer
            }
        }

        if (isSelectedElement) {
            colorBuffer.set(selectionColor, edgesColor)
            return colorBuffer
        }

        val color: Color = element.color ?: defaultFillColor
        colorBuffer.set(color, edgesColor)

        return colorBuffer
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

    fun onEscape() {
        disableSearching()
        currentSelectedElement = null
        view?.redraw()
    }

    fun onMouseClickedToEmptySpace() {
        currentSelectedElement = null
        view?.redraw()
    }

    fun onSearchTextRemoved() {
        disableSearching()
        view?.redraw()
    }

    private fun disableSearching() {
        if (!isSearchingInProgress) {
            return
        }
        dialogView?.exitFromSearching()
        isSearchingInProgress = false
        for (foundElement in foundItems) {
            foundElement.isFoundElement = false
        }
        foundItems.clear()
    }

    fun copySelectedToClipboard() {
        currentSelectedElement?.let {
            copyToClipboard(it.name)
        }
    }

    fun copyShortClassNameToClipboard() {
        currentSelectedElement?.let {
            copyToClipboard(textUtils.shortClassMethodName(it.name))
        }
    }

    fun copyShortClassNameWithoutMethodToClipboard() {
        currentSelectedElement?.let {
            copyToClipboard(textUtils.shortClassName(it.name))
        }
    }

    private fun copyToClipboard(text: String) {
        val stringSelection = StringSelection(text)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(stringSelection, null)
    }

    fun onFindInMethodsPressed(text: String, ignoreCase: Boolean = false) {
        view?.requestFocus()
        val shouldEndsWithText = text.endsWith("()")
        val textToFind = if (shouldEndsWithText) {
            text.substring(0, text.length - 2)
        } else text
        isSearchingInProgress = true
        val targetString = if (ignoreCase) textToFind.toLowerCase() else textToFind

        foundItems.clear()

        for (element in rectangles) {
            val lowerCasedName = if (ignoreCase) element.name.toLowerCase() else element.name
            val isEquals = if (shouldEndsWithText)
                lowerCasedName.endsWith(targetString)
            else
                lowerCasedName.contains(targetString)
            if (isEquals) {
                foundItems.add(element)
                element.isFoundElement = true
            } else {
                element.isFoundElement = false
            }
        }
        if (foundItems.isEmpty()) {
            foundInfoListener?.onNotFound(text, ignoreCase)
            isSearchingInProgress = false
            view?.redraw()
            return
        }
        currentFocusedFoundElement = 0
        val element: FlameRectangle = foundItems[currentFocusedFoundElement]
        foundInfoListener?.onFound(foundItems.size, currentFocusedFoundElement, element)
        view?.fitZoom(element)
    }

    fun focusNextFoundItem() {
        dialogView?.hideInfoPanel()

        if (foundItems.size > 0) {
            currentFocusedFoundElement++
            if (currentFocusedFoundElement >= foundItems.size) {
                currentFocusedFoundElement = 0
            }
            val found = foundItems[currentFocusedFoundElement]
            view?.fitZoom(found)
            foundInfoListener!!.onFound(foundItems.size, currentFocusedFoundElement, found)
        }
    }

    fun focusPrevFoundItem() {
        dialogView?.hideInfoPanel()

        if (foundItems.size > 0) {
            currentFocusedFoundElement--
            if (currentFocusedFoundElement < 0) {
                currentFocusedFoundElement = foundItems.size - 1
            }
            val found = foundItems[currentFocusedFoundElement]
            view?.fitZoom(found)
            foundInfoListener!!.onFound(foundItems.size, currentFocusedFoundElement, found)
        }
    }

    data class ChildHolder(
        var totalDuration: Double = 0.0,
        val children: MutableList<ProfileData> = mutableListOf()
    )

    private data class Result(
        val rectangles: List<FlameRectangle>,
        val minLeftOffset: Double,
        val topOffset: Double,
        val maxRightOffset: Double
    )
}
