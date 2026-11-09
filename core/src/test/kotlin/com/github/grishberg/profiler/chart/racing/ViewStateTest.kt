package com.github.grishberg.profiler.chart.racing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ViewStateTest {

    private fun state() = ViewState(worldWidth = 2400.0, worldHeight = 960.0, scale = 1.0)

    @Test
    fun zoomAtAnchorsTheWorldPointUnderTheCursor() {
        val s = state().apply { viewX = 100.0; viewY = 50.0 }
        val wx = s.worldXUnder(300.0)
        val wy = s.worldYUnder(200.0)
        s.zoomAt(300.0, 200.0, 1.7, 800.0, 600.0)
        assertEquals(wx, s.worldXUnder(300.0), 1e-6)
        assertEquals(wy, s.worldYUnder(200.0), 1e-6)
    }

    @Test
    fun anchorHoldsAcrossRepeatedZooms() {
        val s = state()
        val wx = s.worldXUnder(300.0)
        val wy = s.worldYUnder(200.0)
        repeat(10) { s.zoomAt(300.0, 200.0, 1.2, 800.0, 600.0) }
        assertEquals(wx, s.worldXUnder(300.0), 1e-6)
        assertEquals(wy, s.worldYUnder(200.0), 1e-6)
        assertEquals(Math.pow(1.2, 10.0), s.scale, 1e-9)
    }

    @Test
    fun scrollByClampsViewXAndViewY() {
        val s = state()
        s.scrollBy(1.0e7, 1.0e7, 800.0, 600.0)
        assertEquals(2400.0 - 800.0, s.viewX, 1e-9)
        assertEquals(960.0 - 600.0, s.viewY, 1e-9)
        s.scrollBy(-1.0e7, -1.0e7, 800.0, 600.0)
        assertEquals(0.0, s.viewX, 0.0)
        assertEquals(0.0, s.viewY, 0.0)
    }

    @Test
    fun zoomInClampsAtMaxScale() {
        val s = state()
        repeat(60) { s.zoomAt(400.0, 300.0, 2.0, 800.0, 600.0) }
        assertEquals(ViewState.MAX_SCALE, s.scale, 1e-9)
    }

    @Test
    fun zoomOutStopsExactlyAtFitScale() {
        val s = state()
        repeat(200) { s.zoomAt(400.0, 300.0, 0.5, 800.0, 600.0) }
        // пол = масштаб «всё влезло»; скроллбаров на полу нет
        assertEquals(800.0 / 2400.0, s.scale, 1e-9)
        assertFalse(s.needsHScroll(800.0))
        assertFalse(s.needsVScroll(600.0))
        // повторный зум-аут на полу ничего не двигает
        val atFloor = s.scale
        repeat(10) { s.zoomAt(400.0, 300.0, 0.5, 800.0, 600.0) }
        assertEquals(atFloor, s.scale, 1e-12)
    }

    @Test
    fun zoomOutAfterWindowGrowKeepsScale() {
        // окно выросло: scale уже ниже нового fit-пола → отзум обязан залипнуть,
        // а не «дожимать» масштаб вверх
        val s = state().apply { scale = 0.34 }
        repeat(20) { s.zoomAt(400.0, 300.0, 0.5, 2000.0, 1500.0) }
        assertEquals(0.34, s.scale, 1e-12)
        assertEquals(0.0, s.viewX, 0.0)
        assertEquals(0.0, s.viewY, 0.0)
    }

    @Test
    fun minScaleFloorHoldsWhenWorldDwarfsViewport() {
        val s = state()
        repeat(200) { s.zoomAt(400.0, 300.0, 0.5, 40.0, 20.0) }
        assertEquals(ViewState.MIN_SCALE, s.scale, 1e-9)
    }

    @Test
    fun zoomOutBelowFitPinsViewAtZero() {
        val s = state().apply { scrollBy(500.0, 500.0, 800.0, 600.0) }
        assertTrue(s.viewX > 0.0)
        repeat(30) { s.zoomAt(400.0, 300.0, 0.9, 800.0, 600.0) }
        // мир влезает → viewX/viewY обязаны схлопнуться в 0
        assertTrue(s.worldWidth * s.scale <= 800.0 + 0.5)
        assertEquals(0.0, s.viewX, 0.0)
        assertEquals(0.0, s.viewY, 0.0)
    }

    @Test
    fun needsHScrollNeedsVScrollOnTheFitBoundary() {
        val s = state() // scale=1: мир 2400×960
        assertFalse("exact fit → no h-scroll", s.needsHScroll(2400.0))
        assertFalse("sub-0.5px overflow tolerated", s.needsHScroll(2399.6))
        assertTrue(s.needsHScroll(2399.4))
        assertFalse(s.needsVScroll(960.0))
        assertFalse(s.needsVScroll(959.6))
        assertTrue(s.needsVScroll(959.4))
    }

    @Test
    fun fitHidesBothScrollbars() {
        val s = state()
        s.fit(1000.0, 596.0)
        assertFalse(s.needsHScroll(1000.0))
        assertFalse(s.needsVScroll(596.0))
        assertEquals(0.0, s.viewX, 0.0)
        assertEquals(0.0, s.viewY, 0.0)
        assertEquals(1000.0 / 2400.0, s.scale, 1e-12)
    }

    @Test
    fun zoomAtBeyondContentEdgeClampsWithoutThrowing() {
        val s = state().apply { viewX = 2350.0 }
        s.zoomAt(799.0, 599.0, 3.0, 800.0, 600.0)
        assertTrue(s.viewX in 0.0..(2400.0 - 800.0 / s.scale + 1e-9))
        assertTrue(abs(s.viewY) <= 960.0)
    }

    @Test
    fun anisotropicZoomLeavesVerticalUntouched() {
        val s = state().apply { verticalZoomEnabled = false; yScale = 1.0; viewY = 100.0 }
        s.zoomAt(300.0, 300.0, 2.0, 800.0, 600.0)
        assertEquals(2.0, s.scale, 1e-12)
        assertEquals(100.0, s.viewY, 1e-9)                          // Y не шелохнулся
        assertEquals(400.0, s.worldYUnder(300.0), 1e-9)             // 100 + 300/1.0
    }

    @Test
    fun anisotropicFitFloorIsHorizontalOnly() {
        val s = state().apply { verticalZoomEnabled = false; yScale = 1.0 }
        repeat(10) { s.zoomAt(400.0, 300.0, 1.0 / 1.5, 800.0, 600.0) }
        assertEquals(800.0 / 2400.0, s.scale, 1e-9)                 // пол X-fit, не Y-fit
        assertFalse(s.needsHScroll(800.0))
        assertTrue(s.needsVScroll(600.0))                           // вертикальный скролл остаётся
    }

    @Test
    fun scrollByDividesVerticalDeltaByYScaleWhenVerticalZoomOff() {
        val s = state().apply { verticalZoomEnabled = false; yScale = 2.0 }
        s.scrollBy(0.0, 100.0, 800.0, 600.0)
        assertEquals(50.0, s.viewY, 1e-9)
    }

    @Test
    fun needsVScrollUsesFixedYScaleWhenVerticalZoomOff() {
        val s = state().apply { verticalZoomEnabled = false; yScale = 0.5 }
        assertFalse(s.needsVScroll(600.0))                          // 960*0.5 = 480 влезает
        assertTrue(s.needsHScroll(800.0))                           // X по-прежнему масштабируется
    }

    @Test
    fun focusXFitsRangeAndPinsViewToItsStart() {
        val s = state()
        s.focusX(500.0, 900.0, 800.0)                               // 400 ms в 800px
        assertEquals(2.0, s.scale, 1e-12)
        assertEquals(500.0, s.viewX, 1e-12)
        s.clamp(800.0, 600.0)
        assertEquals(500.0, s.viewX, 1e-9)                          // в мире помещается — не сдвинули
    }

    @Test
    fun focusXClampsScaleToRange() {
        val s = state()
        s.focusX(0.0, 1.0e7, 800.0)                                 // span больше мира → scale на полу
        assertEquals(ViewState.MIN_SCALE, s.scale, 1e-12)
        s.focusX(10.0, 10.0 + 1e-9, 800.0)                          // span почти нулевой → scale на потолке
        assertEquals(ViewState.MAX_SCALE, s.scale, 1e-12)
    }
}
