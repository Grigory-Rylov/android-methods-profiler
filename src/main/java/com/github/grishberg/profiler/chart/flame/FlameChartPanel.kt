package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.chart.ProfilerPanel.TOP_OFFSET
import com.github.grishberg.profiler.ui.ZoomAndPanDelegate
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import javax.swing.JDialog
import javax.swing.JPanel

private const val MINIMUM_WIDTH_IN_PX = 1.0
private const val FIT_PADDING = 8

class FlameChartPanel(
    private val parentDialog: JDialog,
    private val controller: FlameChartController
) : JPanel(), View {
    private val bgColor = Color(65, 65, 65)


    private val leftSymbolOffset = 4
    private val fontTopOffset = 4
    private val zoomAndPanDelegate = ZoomAndPanDelegate(this, TOP_OFFSET, ZoomAndPanDelegate.LeftBottomBounds())
    private var init = true

    override var bounds: Rectangle2D.Double = Rectangle2D.Double()
        set(value) {
            field = value
            zoomAndPanDelegate.updateRightBottomCorner(value.maxX, value.maxY)
        }
    override var levelHeight = 20.0
    override var fontName: String = ""

    init {
        background = bgColor
    }

    override fun showDialog() {
        parentDialog.isVisible = true
    }

    override fun hideDialog() {
        parentDialog.isVisible = false
    }

    override fun redraw() {
        repaint()
    }

    override fun fitZoom(rect: Rectangle2D.Double) {
        zoomAndPanDelegate.setTransform(AffineTransform())
        zoomAndPanDelegate.fitZoom(rect, FIT_PADDING, ZoomAndPanDelegate.VerticalAlign.ENABLED)
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        if (init) {
            // Initialize the viewport by moving the origin to the center of the window,
            // and inverting the y-axis to point upwards.
            init = false
            // Save the viewport to be updated by the ZoomAndPanListener
            val transform = g.transform
            zoomAndPanDelegate.setTransform(transform)
        }
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_GASP
        )
        draw(g)
    }

    private fun draw(g: Graphics2D) {
        val leftTop = zoomAndPanDelegate.transformPoint(Point(0, 0))
        val rightBottom = zoomAndPanDelegate.transformPoint(Point(width, height))
        val screenLeft = leftTop.x.toDouble()
        val screenTop = leftTop.y.toDouble()
        val screenRight = rightBottom.x.toDouble()
        val screenBottom = rightBottom.y.toDouble()

        val msPerPixel = (screenRight - screenLeft) / width
        val minimumSizeInMs = msPerPixel * MINIMUM_WIDTH_IN_PX

        val fm = getFontMetrics(g.font)

        controller.onDraw(g, fm, minimumSizeInMs, screenLeft, screenTop, screenRight, screenBottom)
    }

    override fun drawRect(
        g: Graphics2D, fm: FontMetrics, element: FlameRectangle,
        topOffset: Double, borderColor: Color, fillColor: Color
    ) {
        element.height = levelHeight
        val transformedShape: Shape = zoomAndPanDelegate.transform.createTransformedShape(element)
        val bounds = transformedShape.bounds

        g.color = fillColor
        g.fill(transformedShape)
        g.color = borderColor
        g.draw(transformedShape)

        g.color = Color.BLACK
        drawLabel(g, fm, element.name, bounds, bounds.y + bounds.height - fontTopOffset)

    }

    private fun drawLabel(
        g: Graphics2D,
        fm: FontMetrics,
        name: String,
        horizontalBounds: Rectangle,
        topPosition: Int
    ) {
        var leftPosition: Int = horizontalBounds.x + leftSymbolOffset
        if (leftPosition < 0) {
            leftPosition = 0
        }
        for (element in name) {
            val c = element
            val w = fm.charWidth(c)
            if (leftPosition + w > horizontalBounds.x + horizontalBounds.width) {
                break
            }
            g.drawString(c.toString(), leftPosition, topPosition)
            leftPosition += w
        }
    }

    override fun drawRect(g: Graphics2D, rect: Rectangle2D, color: Color) {
        val transformedShape: Shape = zoomAndPanDelegate.transform.createTransformedShape(rect)
        g.color = color
        g.draw(transformedShape)
    }

    fun resetZoom() {
        fitZoom(bounds)
    }

    fun zoomOut() {

    }

    fun zoomIn() {

    }

    fun scrollRight() {

    }

    fun scrollLeft() {


    }

    fun scrollUp() {

    }

    fun scrollDown() {

    }

    fun removeSelection() {

    }

    fun increaseFontSize() {

    }

    fun decreaseFontSize() {

    }

    fun centerSelectedElement() {

    }

    fun fitSelectedElement() {

    }

    fun findDataByPosition(point: Point): FlameRectangle? {
        val transformedPoint = zoomAndPanDelegate.transformPoint(point)
        return controller.findDataByPosition(transformedPoint)
    }
}
