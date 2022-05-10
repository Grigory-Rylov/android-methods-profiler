package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.child
import com.github.grishberg.profiler.comparator.aggregator.FlameChartAggregator
import com.github.grishberg.profiler.countChildren
import com.github.grishberg.profiler.get
import com.github.grishberg.profiler.profileData
import com.github.grishberg.profiler.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 0.001

internal class FlameChartAggregatorTest {
    private val trace1 = profileData("A", 0.0, 100.0)
        .apply {
            child("B", 0.0, 20.0)
                .child("C", 10.0, 20.0)
            child("D", 20.0, 100.0).apply {
                child("B", 20.0, 30.0)
                    .child("C", 22.0, 30.0)
                child("C", 30.0, 40.0)
                child("B", 50.0, 60.0)
                    .child("C", 52.0, 60.0)
                child("B", 60.0, 80.0)
            }
        }

    private val trace2 = profileData("A", 0.0, 200.0)
        .apply {
            child("B", 0.0, 60.0)
                .child("C", 10.0, 60.0)
            child("D", 60.0, 200.0).apply {
                child("B", 60.0, 80.0)
                    .child("C", 70.0, 80.0)
                child("B", 90.0, 110.0)
                    .child("C", 100.0, 110.0)
                child("B", 110.0, 140.0)
                child("B", 150.0, 180.0)
                    .child("C", 150.0, 180.0)
                child("E", 190.0, 200.0)
            }
        }

    private val trace3 = profileData("A", 0.0, 150.0)
        .apply {
            child("B", 0.0, 40.0)
                .child("C", 10.0, 40.0)
            child("D", 40.0, 150.0).apply {
                child("B", 40.0, 70.0)
                    .child("C", 40.0, 60.0)
                child("B", 80.0, 110.0)
                child("B", 110.0, 130.0)
                    .child("C", 110.0, 130.0)
                child("E", 140.0, 150.0)
            }
        }

    private val trace4 = profileData("A", 0.0, 100.0)
        .apply {
            child("B", 0.0, 20.0).apply {
                child("F", 0.0, 10.0)
                child("C", 10.0, 20.0)
            }
            child("D", 20.0, 100.0).apply {
                child("B", 30.0, 40.0)
                    .child("C", 30.0, 40.0)
                child("B", 40.0, 60.0)
                    .child("C", 40.0, 50.0)
                child("B", 80.0, 90.0)
                child("B", 60.0, 80.0)
                    .child("C", 60.0, 80.0)
                child("E", 90.0, 100.0)
            }
        }

    private val trace5 = profileData("A", 0.0, 80.0)
        .apply {
            child("B", 0.0, 15.0).apply {
                child("F", 0.0, 10.0)
                child("C", 10.0, 15.0)
            }
            child("D", 15.0, 80.0).apply {
                child("B", 15.0, 30.0)
                    .child("C", 20.0, 30.0)
                child("C", 30.0, 40.0)
                child("B", 40.0, 50.0)
                    .child("C", 45.0, 50.0)
                child("B", 50.0, 60.0)
                    .child("C", 50.0, 60.0)
                child("B", 70.0, 80.0)
            }
        }

    private val underTest = FlameChartAggregator()

    @Test
    fun `aggregate test`() {
        val traces = listOf(
            listOf(trace1),
            listOf(trace2),
            listOf(trace3),
            listOf(trace4),
            listOf(trace5)
        )
        val aggregated = underTest.aggregate(traces, "test")
        val expectedRootWidth = traces.flatten().sumOf { it.width() } / traces.size

        assertEquals("test", aggregated.name)
        assertEquals(1.0, aggregated.mean, DELTA)
        assertEquals(1, aggregated.children.size)
        assertEquals(expectedRootWidth, aggregated.width, DELTA)

        val a = aggregated.children.first()
        assertEquals("A", a.name)
        assertEquals(1.0, a.mean, DELTA)
        assertEquals(2, a.children.size)
        assertEquals(expectedRootWidth, a.width, DELTA)

        val ab = a.children.first()
        assertEquals("B", ab.name)
        assertEquals(1.0, ab.mean, DELTA)
        assertEquals(1, ab.children.size)
        assertTrue(ab.left >= a.left)
        assertTrue(ab.width <= a.width)

        val abc = ab.children.first()
        assertEquals("C", abc.name)
        assertEquals(1.0, abc.mean, DELTA)
        assertEquals(0, abc.children.size)
        assertTrue(abc.left >= ab.left)
        assertTrue(abc.width <= ab.width)

        val ad = a.children[1]
        assertEquals("D", ad.name)
        assertEquals(1.0, ad.mean, DELTA)
        assertEquals(2, ad.children.size)
        assertTrue(ad.left >= a.left)
        assertTrue(ad.width <= a.width)

        assertTrue(ab.width < ad.width)

        val adb = ad.children.first()
        val countB = traces.flatten().sumOf { root -> root.get("D").countChildren("B") }
        val expectedMeanB = countB.toDouble() / traces.size
        assertEquals("B", adb.name)
        assertEquals(expectedMeanB, adb.mean, DELTA)
        assertEquals(1, adb.children.size)
        assertTrue(adb.left >= ad.left)
        assertTrue(adb.width <= ad.width)

        val adbc = adb.children.first()
        val countC = traces.flatten().sumOf { root -> root.get("D").get("B").countChildren("C") }
        val expectedMeanC = countC.toDouble() / traces.size
        assertEquals("C", adbc.name)
        assertEquals(expectedMeanC, adbc.mean, DELTA)
        assertEquals(0, adbc.children.size)
        assertTrue(adbc.left >= adb.left)
        assertTrue(adbc.width <= adb.width)

        val ade = ad.children[1]
        assertEquals("E", ade.name)
        assertEquals(1.0, ade.mean, DELTA)
        assertEquals(0, ade.children.size)
        assertTrue(ade.left >= ad.left)
        assertTrue(ade.width <= ad.width)

        assertTrue(adb.width > ade.width)
    }
}
