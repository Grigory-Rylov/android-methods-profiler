package com.github.grishberg.profiler.chart.racing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class WheelGestureTest {

    @Test
    fun plainWheelScrollsHorizontally() {
        val g = WheelGesture.classify(isShiftDown = false, isCtrlDown = false, rotation = 1.0)
        assertTrue(g is WheelGesture.Scroll)
        assertEquals(WheelGesture.SCROLL_PX_PER_ROTATION, (g as WheelGesture.Scroll).dxPx, 1e-9)
    }

    @Test
    fun wheelUpScrollsLeftDownScrollsRight() {
        val up = WheelGesture.classify(false, false, -1.0) as WheelGesture.Scroll
        val down = WheelGesture.classify(false, false, 1.0) as WheelGesture.Scroll
        assertTrue(up.dxPx < 0.0)
        assertTrue(down.dxPx > 0.0)
    }

    @Test
    fun shiftWheelZoomsAtCursorAnchorFactor() {
        val g = WheelGesture.classify(isShiftDown = true, isCtrlDown = false, rotation = -1.0)
        assertTrue(g is WheelGesture.Zoom)
        assertEquals(exp(WheelGesture.ZOOM_EXP), (g as WheelGesture.Zoom).factor, 1e-9)
    }

    @Test
    fun ctrlWheelZoomsLikePinch() {
        val g = WheelGesture.classify(isShiftDown = false, isCtrlDown = true, rotation = -1.0)
        assertTrue(g is WheelGesture.Zoom)
        assertEquals(exp(WheelGesture.PINCH_ZOOM_EXP), (g as WheelGesture.Zoom).factor, 1e-9)
    }

    @Test
    fun shiftTakesPriorityOverCtrl() {
        val g = WheelGesture.classify(isShiftDown = true, isCtrlDown = true, rotation = 1.0)
        assertTrue(g is WheelGesture.Zoom)
        assertEquals(exp(-WheelGesture.ZOOM_EXP), (g as WheelGesture.Zoom).factor, 1e-9)
    }

    @Test
    fun zeroRotationWithoutModifiersIsIgnored() {
        assertTrue(WheelGesture.classify(false, false, 0.0) is WheelGesture.Ignored)
    }

    @Test
    fun wheelScrollFeedsViewStateHorizontally() {
        val s = ViewState(worldWidth = 2400.0, worldHeight = 960.0, scale = 1.0)
        val g = WheelGesture.classify(false, false, 1.0) as WheelGesture.Scroll
        s.scrollBy(g.dxPx, 0.0, 800.0, 600.0)
        assertEquals(g.dxPx, s.viewX, 1e-9)
        assertEquals(0.0, s.viewY, 1e-9)
    }

    @Test
    fun shiftWheelZoomKeepsWorldPointUnderCursor() {
        val s = ViewState(worldWidth = 2400.0, worldHeight = 960.0, scale = 1.0)
        val anchor = s.worldXUnder(300.0)
        repeat(5) {
            val g = WheelGesture.classify(true, false, -1.0) as WheelGesture.Zoom
            s.zoomAt(300.0, 200.0, g.factor, 800.0, 600.0)
        }
        assertEquals(anchor, s.worldXUnder(300.0), 1e-6)
    }
}
