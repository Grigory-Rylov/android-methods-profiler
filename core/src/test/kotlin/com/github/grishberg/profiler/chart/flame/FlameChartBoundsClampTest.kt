package com.github.grishberg.profiler.chart.flame

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

class FlameChartBoundsClampTest {

    private val strategy = FlameChartZoomAndPanDelegate.LeftBottomBounds()

    private fun screen(minX: Double, worldWidth: Double) =
        Rectangle2D.Double(minX, 0.0, worldWidth, 500.0)

    @Test
    fun panCannotScrollPastContentLeftEdge() {
        val corrected = strategy.correctOffset(
            100.0, 0.0, screen(50.0, 800.0), Point2D.Double(2000.0, 900.0)
        )
        assertEquals(50.0, corrected.x, 1e-9)
    }

    @Test
    fun panCannotScrollPastContentRightEdge() {
        val corrected = strategy.correctOffset(
            -100.0, 0.0, screen(1500.0, 800.0), Point2D.Double(2000.0, 900.0)
        )
        assertEquals(300.0, corrected.x, 1e-9)
    }

    @Test
    fun panInsideContentRangeIsNotCorrected() {
        val corrected = strategy.correctOffset(
            100.0, 0.0, screen(500.0, 800.0), Point2D.Double(2000.0, 900.0)
        )
        assertEquals(100.0, corrected.x, 1e-9)
    }

    @Test
    fun panCannotMoveNarrowContentAwayFromScreenLeftEdge() {
        val corrected = strategy.correctOffset(
            -100.0, 0.0, screen(0.0, 800.0), Point2D.Double(500.0, 900.0)
        )
        assertEquals(0.0, corrected.x, 1e-9)
    }

    @Test
    fun narrowContentCannotScrollOffRightSideOfScreen() {
        val corrected = strategy.correctOffset(
            500.0, 0.0, screen(0.0, 800.0), Point2D.Double(500.0, 900.0)
        )
        assertEquals(300.0, corrected.x, 1e-9)
    }

    @Test
    fun verticalOffsetUnchangedWhenDataIsBelowScreenBottom() {
        val corrected = strategy.correctOffset(
            0.0, -100.0, screen(100.0, 800.0), Point2D.Double(2000.0, 900.0)
        )
        assertEquals(-100.0, corrected.y, 1e-9)
    }

    @Test
    fun verticalOffsetClampedSoContentBottomStaysOnScreenBottom() {
        val corrected = strategy.correctOffset(
            0.0, -100.0, screen(100.0, 800.0), Point2D.Double(2000.0, 450.0)
        )
        assertEquals(50.0, corrected.y, 1e-9)
    }
}
