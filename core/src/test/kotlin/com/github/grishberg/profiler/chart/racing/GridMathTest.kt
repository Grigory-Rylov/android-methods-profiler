package com.github.grishberg.profiler.chart.racing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

class GridMathTest {

    @Test
    fun stepGrowsWhenPxPerUnitShrinksAndMajorSpacingStaysAboveMinPx() {
        val pxs = listOf(50.0, 10.0, 1.0, 0.1, 0.05, 0.02)
        val steps = pxs.map { GridMath.niceStep(it) }
        for (i in 1 until steps.size) {
            assertTrue("step must grow on zoom-out: $steps", steps[i] > steps[i - 1])
        }
        for ((px, step) in pxs.zip(steps)) {
            assertTrue("step*px=$step*$px < minPx", step * px >= 70.0 - 1e-9)
        }
    }

    @Test
    fun niceStepReturnsDTimesTenToTheNWithDIn125AndIsMinimal() {
        var px = 0.02
        while (px <= 50.0) {
            val step = GridMath.niceStep(px)
            assertTrue("bad nice step $step at px=$px", GridMath.leadingDigit(step) in listOf(1, 2, 5))
            // минимальность: предыдущий кандидат уже не проходит по minPx
            val prev = when (GridMath.leadingDigit(step)) {
                1 -> step / 2.0 // …2·10^(n-1), 5·10^(n-1)=0.5·10^n
                2 -> step / 2.0 // …10^n
                else -> step / 2.5 // d=5 → 2·10^n
            }
            assertTrue("not minimal: prev=$prev passes at px=$px", prev * px < 70.0 - 1e-9)
            px *= 1.31
        }
    }

    @Test
    fun niceStepIsFloatingPointStableOnExactBoundaries() {
        assertEquals(100.0, GridMath.niceStep(0.7), 1e-9)
        assertEquals(0.001, GridMath.niceStep(70_000.0), 1e-12)
        assertEquals(1.0, GridMath.niceStep(70.0), 1e-12)
        assertEquals(50.0, GridMath.niceStep(3.3), 1e-9) // raw=21.2 → 50 (20*3.3=66 < 70)
    }

    @Test
    fun minorDivisionsIsFiveForD1AndD5AndFourForD2() {
        assertEquals(5, GridMath.minorDivisions(100.0))
        assertEquals(5, GridMath.minorDivisions(500.0))
        assertEquals(5, GridMath.minorDivisions(0.01))
        assertEquals(4, GridMath.minorDivisions(200.0))
        assertEquals(4, GridMath.minorDivisions(0.02))
    }

    @Test
    fun minorStepForSwitchesExactlyAtTheMajorIntervalTrigger() {
        val pxUnder = 119.9 / 50.0 // niceStep=50 → major-интервал 119.9px
        val pxOver = 120.1 / 50.0 // 120.1px
        assertEquals(50.0, GridMath.niceStep(pxUnder), 1e-12)
        assertEquals(50.0, GridMath.niceStep(pxOver), 1e-12)
        assertNull(GridPainter.minorStepFor(pxUnder))
        assertNotNull(GridPainter.minorStepFor(pxOver))
        assertEquals(50.0 / GridMath.minorDivisions(50.0), GridPainter.minorStepFor(pxOver)!!, 1e-12)
        // Согласованность с niceStep/minorDivisions на всей линейке scale
        var px = 0.02
        while (px <= 50.0) {
            val s = GridMath.niceStep(px)
            val expected = if (s * px >= GridPainter.MINOR_TRIGGER_PX) s / GridMath.minorDivisions(s) else null
            assertTrue("px=$px", expected == GridPainter.minorStepFor(px))
            px *= 1.31
        }
    }

    @Test
    fun painterShowsMinorGridOnlyAtDeepZoom() {
        val deep = spec(2.6) // major 130px — сильный зум: minor появляются
        assertNotNull("minor must appear at deep zoom", deep.minorStep)
        val mid = spec(0.5) // major 100px — средний зум: только редкие major
        assertNull("minor must vanish at mid zoom", mid.minorStep)
        assertTrue(spec(0.05).step > deep.step)
        assertTrue(deep.majorLineCount > 0)
    }

    @Test
    fun minorStepEqualsStepOverMinorDivisions() {
        val spec = specOf(3.0)
        assertEquals(spec.step / GridMath.minorDivisions(spec.step), spec.minorStep!!, 1e-9)
    }

    private fun spec(scale: Double): GridSpec {
        val img = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            return GridPainter.paint(g, ViewState(scale = scale), 400, 200, RacingColorScheme.DARK)
        } finally {
            g.dispose()
        }
    }

    private fun specOf(scale: Double): GridSpec {
        val img = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            return GridPainter.paint(g, ViewState(scale = scale), 200, 100, RacingColorScheme.DARK)
        } finally {
            g.dispose()
        }
    }
}
