package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.chart.MethodsNameDrawer
import com.github.grishberg.profiler.chart.racing.CallRow
import com.github.grishberg.profiler.chart.racing.RacingColorScheme
import com.github.grishberg.profiler.chart.racing.VirtualSpacePanel
import com.github.grishberg.profiler.chart.racing.VirtualSpaceView
import com.github.grishberg.profiler.ui.theme.Palette
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.util.IdentityHashMap
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.roundToInt

private val DEFAULT_FILL_COLOR = Color(0xD7AE65)
private const val FIT_PADDING = 8.0
private const val KEYBOARD_ZOOM = 1.5
private const val MIN_WORLD_SIZE = 1e-6

class FlameRacingView(
    private val parentDialog: JFrame,
    private val controller: FlameChartController,
    private val palette: Palette,
) : View {
    val view = VirtualSpaceView()
    val panel: VirtualSpacePanel get() = view.panel
    var onElementSelected: ((FlameRectangle) -> Unit)? = null
    var onElementHovered: ((FlameRectangle?, Point) -> Unit)? = null

    private val colorScheme = RacingColorScheme.forPalette(palette)
    private val nameDrawer = MethodsNameDrawer()
    private val rowToElement = IdentityHashMap<CallRow, FlameRectangle>()
    private val elementToRow = IdentityHashMap<FlameRectangle, CallRow>()
    private var xShift = 0.0

    override var bounds: Rectangle2D.Double = Rectangle2D.Double()
        set(value) {
            field = value
            rebuildRows(value)
        }
    override var levelHeight = 20.0
    override var fontName: String = ""

    init {
        view.setColors(colorScheme)
        view.verticalScalingEnabled = false
        panel.labelPainter = nameDrawer
        panel.colorOverrideProvider = { row ->
            rowToElement[row]?.color ?: DEFAULT_FILL_COLOR
        }
        panel.componentPopupMenu = ElementPopupMenu(controller)
        panel.onRowClicked = { row, _ ->
            selectElement(row?.let { rowToElement[it] })
        }
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) return
                selectElement(elementAt(e.point))
            }

            override fun mouseExited(e: MouseEvent) {
                onElementHovered?.invoke(null, e.point)
            }
        })
        panel.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                onElementHovered?.invoke(elementAt(e.point), e.point)
            }
        })
    }

    override fun redraw() {
        panel.selectedRow = controller.currentSelectedElement?.let { elementToRow[it] }
        panel.repaint()
        view.header.repaint()
    }

    override fun fitZoom(rect: Rectangle2D.Double) {
        if (rect === bounds) {
            fitToAll()
            return
        }
        if (rect is FlameRectangle) {
            val row = elementToRow[rect] ?: return
            panel.fitToRow(row, FIT_PADDING, true)
        } else {
            panel.fitToRange(rect.x - xShift, rect.maxX - xShift, FIT_PADDING, true)
        }
    }

    override fun onFoundRowsChanged(found: List<FlameRectangle>, focusedIndex: Int) {
        val rows = found.mapNotNull { elementToRow[it] }
        if (rows.isEmpty()) {
            panel.clearFoundRows()
        } else {
            panel.renderFoundRows(rows)
        }
        panel.setFocusedFoundIndex(focusedIndex)
    }

    override fun showDialog() {
        parentDialog.isVisible = true
    }

    override fun hideDialog() {
        parentDialog.isVisible = false
    }

    override fun requestFocus() {
        panel.requestFocusInWindow()
    }

    override fun drawRect(
        g: Graphics2D, fm: FontMetrics, rect: FlameRectangle,
        topOffset: Double, borderColor: Color, fillColor: Color
    ) = Unit

    fun elementAt(point: Point): FlameRectangle? =
        panel.rowUnderScreen(point)?.let { rowToElement[it] }

    fun refreshPalette() {
        colorScheme.applyPalette(palette)
        view.setColors(colorScheme)
        panel.invalidateColorCache()
        panel.repaint()
    }

    fun resetZoom() = fitToAll()

    fun zoomIn() = panel.zoomAt(panel.width / 2.0, panel.height / 2.0, KEYBOARD_ZOOM)

    fun zoomOut() = panel.zoomAt(panel.width / 2.0, panel.height / 2.0, 1.0 / KEYBOARD_ZOOM)

    fun panBy(dxPx: Double, dyPx: Double) = panel.panBy(dxPx, dyPx)

    fun centerSelectedElement() {
        val row = panel.selectedRow
            ?: controller.currentSelectedElement?.let { elementToRow[it] } ?: return
        panel.centerOnRow(row, vertical = true)
    }

    fun fitSelectedElement() {
        val element = controller.currentSelectedElement
            ?: panel.focusedFoundRow?.let { rowToElement[it] }
        val row = element?.let { elementToRow[it] } ?: return
        panel.fitToRow(row, FIT_PADDING, true)
    }

    fun increaseFontSize() {
        view.fontHeightPx = panel.fontHeightPx + 1
    }

    fun decreaseFontSize() {
        view.fontHeightPx = panel.fontHeightPx - 1
    }

    fun requestPanelFocus() {
        panel.requestFocusInWindow()
    }

    private fun fitToAll() {
        panel.fitToRange(0.0, max(bounds.width, MIN_WORLD_SIZE), 0.0, topVertical = true)
    }

    private fun selectElement(element: FlameRectangle?) {
        if (element == null) {
            controller.onMouseClickedToEmptySpace()
            return
        }
        controller.selectElement(element)
        onElementSelected?.invoke(element)
    }

    private fun rebuildRows(newBounds: Rectangle2D.Double) {
        val rects = controller.currentRectangles
        rowToElement.clear()
        elementToRow.clear()
        if (rects.isEmpty()) {
            xShift = 0.0
            view.state.timeOffset = 0.0
            view.setWorldSize(MIN_WORLD_SIZE, VirtualSpacePanel.ROW_H)
            view.setData(emptyList())
            return
        }
        xShift = if (newBounds.width > 0.0) newBounds.x else rects.minOf { it.x }
        val rows = ArrayList<CallRow>(rects.size)
        var maxDepth = 0
        for (rect in rects) {
            val unitHeight = if (rect.height > 0.0) rect.height else levelHeight
            val depth = ((rect.y / unitHeight).roundToInt()).coerceAtLeast(0)
            val row = CallRow(depth, rect.name, rect.x - xShift, max(rect.width, 0.0))
            rowToElement[row] = rect
            elementToRow[rect] = row
            rows.add(row)
            if (depth > maxDepth) maxDepth = depth
        }
        view.state.timeOffset = -xShift
        view.setWorldSize(
            max(newBounds.width, MIN_WORLD_SIZE),
            (maxDepth + 1) * VirtualSpacePanel.ROW_H,
        )
        view.setData(rows)
    }
}
