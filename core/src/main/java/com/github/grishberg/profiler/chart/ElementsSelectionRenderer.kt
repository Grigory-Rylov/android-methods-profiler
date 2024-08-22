package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.core.ProfileData
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform

interface SelectionRenderer {
    val currentThreadId: Int

    fun setCurrentThreadId(id: Int)

    fun draw(
        g: Graphics2D, at: AffineTransform, fm: FontMetrics, threadId: Int,
        selected: ProfileRectangle?,
        minimumSizeInMs: Double,
        screenLeft: Double,
        screenTop: Double,
        screenRight: Double,
        screenBottom: Double
    )

    fun addCallerRectangle(profileData: ProfileData)
    fun addCallTraceItems(profileData: ProfileData)
    fun clear()
    fun removeRectanglesUntilCurrent(factoryMethod: ProfileData)
}

class ElementsSelectionRenderer(
    private val dimensionDelegate: ProfileDataDimensionDelegate,
    private val chartPaintDelegate: ChartPaintDelegate
) : SelectionRenderer {
    private val traceItemsSelectedTextColor: Color = Color(0xF5D249)
    private val traceItemsSelectedFillColor: Color = Color(0x665A5A)
    private val traceItemsSelectedBorderColor: Color = Color(0xF15932)

    private val traceItemsTextColor: Color = Color(0xFFFFFF)
    private val traceItemsFillColor: Color = Color(0x000000)
    private val traceItemsBorderColor: Color = Color(0x791900)
    private val callerItemFillColor: Color = Color(0x436C3F)
    private val callerItemBorderColor: Color = Color(0x9CFF94)
    private val callTraceItems = mutableListOf<ProfileRectangle>()
    private var callerRectangles = mutableListOf<ProfileRectangle>()

    private var _currentThreadId = -1
    override val currentThreadId
        get() = _currentThreadId

    override fun setCurrentThreadId(id: Int) {
        _currentThreadId = id
    }

    override fun draw(
        g: Graphics2D, at: AffineTransform, fm: FontMetrics, threadId: Int,
        selected: ProfileRectangle?,
        minimumSizeInMs: Double,
        screenLeft: Double,
        screenTop: Double,
        screenRight: Double,
        screenBottom: Double
    ) {
        if (threadId != currentThreadId) {
            return
        }

        for (element in callTraceItems) {
            if (!element.isInScreen(screenLeft, screenTop, screenRight, screenBottom)) {
                continue
            }

            if (element.width < minimumSizeInMs) {
                continue
            }

            val transformedShape = at.createTransformedShape(element)

            val borderColor = if (element == selected) traceItemsSelectedBorderColor else traceItemsBorderColor
            val fillColor = if (element == selected) traceItemsSelectedFillColor else traceItemsFillColor
            val textColor = if (element == selected) traceItemsSelectedTextColor else traceItemsTextColor

            g.color = fillColor
            g.fill(transformedShape)

            g.color = borderColor
            g.draw(transformedShape)

            val bounds = transformedShape.bounds
            g.color = textColor
            chartPaintDelegate.drawLabel(
                g,
                fm,
                element.profileData.name,
                bounds,
                bounds.y + bounds.height - dimensionDelegate.fontTopOffset()
            )
        }

        for (element in callerRectangles) {
            val transformedShape = at.createTransformedShape(element)
            val bounds = transformedShape.bounds

            g.color = callerItemFillColor
            g.fill(transformedShape)

            g.color = callerItemBorderColor
            g.draw(transformedShape)

            g.color = Color.WHITE
            chartPaintDelegate.drawLabel(
                g,
                fm,
                element.profileData.name,
                bounds,
                bounds.y + bounds.height - dimensionDelegate.fontTopOffset()
            )
        }
    }

    override fun addCallerRectangle(profileData: ProfileData) {
        callerRectangles.add(createProfileRectangle(profileData))
    }

    override fun addCallTraceItems(profileData: ProfileData) {
        callTraceItems.add(createProfileRectangle(profileData))
    }

    private fun createProfileRectangle(parent: ProfileData): ProfileRectangle {
        val top: Double = dimensionDelegate.calculateTopForLevel(parent.level)
        val left: Double = dimensionDelegate.calculateStartXForTime(parent)
        val right: Double = dimensionDelegate.calculateEndXForTime(parent)
        val width = right - left
        return ProfileRectangle(
            left,
            top,
            width,
            dimensionDelegate.levelHeight(),
            parent
        )
    }

    override fun clear() {
        _currentThreadId = -1
        callTraceItems.clear()
        callerRectangles.clear()
    }

    override fun removeRectanglesUntilCurrent(factoryMethod: ProfileData) {
        while (callerRectangles.isNotEmpty()) {
            val element = callerRectangles.last()
            callerRectangles.remove(element)
            if (element.profileData == factoryMethod) {
                return
            }
        }
    }
}
