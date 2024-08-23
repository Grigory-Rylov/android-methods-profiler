package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.core.ExtendedData
import com.github.grishberg.profiler.core.ProfileData
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Point
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JPanel

class InfoPanel(private val parent: JPanel) : JComponent() {

    private var x = 0
    private var y = 0
    private val textSize = 13
    private var classNameText: String? = null
    private var rangeText: String? = null
    private var durationText: String? = null
    private var isUserCodeText: String? = null
    private var fileName: String? = null
    private var vAddress: String? = null

    private val backgroundColor = Color(47, 47, 47)
    private val labelColor = Color(191, 198, 187)
    private val font: Font
    private var isThreadTime = false
    private val fontMetrics: FontMetrics
    private var rectHeight = 0
    private var rectWidth = 0
    private var classNameRect: Rectangle2D = Rectangle2D.Double()
    private var rangeRect: Rectangle2D = Rectangle2D.Double()
    private var durationRect: Rectangle2D = Rectangle2D.Double()
    private var isUserCodeRect: Rectangle2D = Rectangle2D.Double()
    private var fileNameRect: Rectangle2D = Rectangle2D.Double()
    private var vAddressRect: Rectangle2D = Rectangle2D.Double()

    init {
        hidePanel()
        font = Font("Arial", Font.BOLD, textSize)
        fontMetrics = getFontMetrics(font)
    }

    fun changeTimeMode(isThreadTime: Boolean) {
        this.isThreadTime = isThreadTime
    }

    fun hidePanel() {
        isVisible = false
    }

    // use the xy coordinates to update the mouse cursor text/label
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.font = font
        if (rectWidth == 0 || rectHeight == 0) {
            return
        }
        var topOffset: Int
        g.color = backgroundColor
        g.fillRect(x, y, rectWidth, rectHeight)
        g.color = labelColor
        topOffset = this.y + textSize + PADDING
        g.drawString(classNameText, x + PADDING, topOffset)
        topOffset += TEXT_INNER_SPACE + classNameRect.height.toInt()
        g.drawString(rangeText, x + PADDING, topOffset)
        topOffset += TEXT_INNER_SPACE + durationRect.height.toInt()
        g.drawString(durationText, x + PADDING, topOffset)

        fileName?.let {
            topOffset += TEXT_INNER_SPACE + fileNameRect.height.toInt()
            g.drawString(fileName, x + PADDING, topOffset)
        }

        vAddress?.let {
            topOffset += TEXT_INNER_SPACE + vAddressRect.height.toInt()
            g.drawString(vAddress, x + PADDING, topOffset)
        }

        isUserCodeText?.let {
            topOffset += TEXT_INNER_SPACE + isUserCodeRect.height.toInt()
            g.drawString(isUserCodeText, x + PADDING, topOffset)
        }
    }

    fun setText(point: Point, selectedData: ProfileData) {
        val parentLocation = parent.location
        val topOffset = parentLocation.y
        val horizontalOffset = parentLocation.x
        val start: Double
        val end: Double
        if (isThreadTime) {
            start = selectedData.threadStartTimeInMillisecond
            end = selectedData.threadEndTimeInMillisecond
        } else {
            start = selectedData.globalStartTimeInMillisecond
            end = selectedData.globalEndTimeInMillisecond
        }
        rangeText = String.format("%.3f ms - %.3f ms", start, end)
        durationText = String.format("%.3f ms", end - start)
        classNameText = selectedData.name
        classNameRect = fontMetrics.getStringBounds(classNameText, null)
        rangeRect = fontMetrics.getStringBounds(rangeText, null)
        durationRect = fontMetrics.getStringBounds(durationText, null)
        rectHeight =
            PADDING * 2 + classNameRect.height.toInt() + rangeRect.height.toInt() + durationRect.height.toInt() + TEXT_INNER_SPACE * 2 + PADDING / 2

        val extendedData = selectedData.extendedData
        if (extendedData is ExtendedData.CppFunctionData) {
            isUserCodeText = if (extendedData.isUserCode) "user code" else "non user code"
            fileName = extendedData.fileName
            vAddress = "vAddress: ${extendedData.vAddress.toString(16)}"
            isUserCodeRect = fontMetrics.getStringBounds(isUserCodeText, null)
            fileNameRect = fontMetrics.getStringBounds(fileName, null)
            vAddressRect = fontMetrics.getStringBounds(vAddress, null)
            rectHeight += TEXT_INNER_SPACE * 3 + (isUserCodeRect.height + fileNameRect.height + vAddressRect.height).toInt()
        } else {
            isUserCodeText = null
            fileName = null
            vAddress = null
        }


        rectWidth = PADDING + Math.max(
            durationRect.width, Math.max(classNameRect.width, rangeRect.width)
        ).toInt() + PADDING
        x = point.x + horizontalOffset + LEFT_PANEL_OFFSET
        y = point.y + topOffset + TOP_PANEL_OFFSET
        if (point.x - (rectWidth + horizontalOffset + LEFT_PANEL_OFFSET) > 0 || x + rectWidth - horizontalOffset > parent.width && point.x + horizontalOffset > parent.width * 0.66) {
            x = point.x - (rectWidth + horizontalOffset + LEFT_PANEL_OFFSET)
        }
        if (y + rectHeight - topOffset > parent.height) {
            y = point.y + topOffset - rectHeight
        }
        isVisible = true


        repaint()
    }

    companion object {

        private const val PADDING = 10
        private const val TEXT_INNER_SPACE = 4
        private const val TOP_PANEL_OFFSET = PADDING
        private const val LEFT_PANEL_OFFSET = PADDING
    }
}
