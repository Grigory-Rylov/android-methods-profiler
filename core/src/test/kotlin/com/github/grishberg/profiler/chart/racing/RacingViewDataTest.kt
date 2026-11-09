package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.analyzer.ThreadTimeBoundsImpl
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ExtendedData
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.core.ThreadItem
import com.github.grishberg.profiler.core.ThreadTimeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingViewDataTest {

    private class FakeProfileData(
        override val name: String,
        override val level: Int,
        private val globalStart: Double,
        private val globalEnd: Double,
        private val threadStart: Double,
        private val threadEnd: Double,
    ) : ProfileData {
        override val threadStartTimeInMillisecond: Double get() = threadStart
        override val globalStartTimeInMillisecond: Double get() = globalStart
        override val threadEndTimeInMillisecond: Double get() = threadEnd
        override val globalEndTimeInMillisecond: Double get() = globalEnd
        override val threadSelfTime = 0.0
        override val globalSelfTime = 0.0
        override val parent: ProfileData? = null
        override val children: List<ProfileData> = emptyList()
        override val extendedData: ExtendedData? = null
    }

    private class FakeThreadItem(
        override val name: String,
        override val threadId: Int,
    ) : ThreadItem

    private class FakeResult(
        private val records: List<ProfileData>,
        override val threadTimeBounds: Map<Int, ThreadTimeBounds>,
        override val globalTimeBounds: Map<Int, ThreadTimeBounds>,
    ) : AnalyzerResult {
        override val maxLevel = 2
        override val data = mapOf(THREAD_ID to records)
        override val threads = listOf(FakeThreadItem("main", THREAD_ID))
        override val mainThreadId = THREAD_ID
        override val startTimeUs = 0L
        override val minThreadTime = 0.0
        override val minGlobalTime = 0.0
    }

    private fun bounds(min: Double, max: Double) = ThreadTimeBoundsImpl(min, max)

    @Test
    fun globalModeMapsGlobalTimesAndDepth() {
        val root = FakeProfileData("a.b.c.Main.run", level = 0,
            globalStart = 10.0, globalEnd = 110.0, threadStart = 1.0, threadEnd = 50.0)
        val child = FakeProfileData("x.y.Z.render", level = 1,
            globalStart = 20.0, globalEnd = 30.0, threadStart = 5.0, threadEnd = 15.0)
        val result = FakeResult(
            listOf(root, child),
            threadTimeBounds = mapOf(THREAD_ID to bounds(1.0, 50.0)),
            globalTimeBounds = mapOf(THREAD_ID to bounds(10.0, 110.0)),
        )

        val data = RacingViewDataFactory.create(result, THREAD_ID, isThreadTime = false)

        assertEquals(2, data.rows.size)
        assertEquals(listOf(0, 1), data.rows.map { it.depth })
        assertEquals(10.0, data.rows[0].startMs, 1e-9)
        assertEquals(100.0, data.rows[0].durMs, 1e-9)
        assertEquals(20.0, data.rows[1].startMs, 1e-9)
        assertEquals(10.0, data.rows[1].durMs, 1e-9)
        assertSame(root, data.rows[0].data)
        assertSame(child, data.rows[1].data)
        // мир: от 0 до конца трейса; fit — bounds потока
        assertEquals(110.0, data.worldWidth, 1e-9)
        assertEquals(3 * VirtualSpacePanel.ROW_H, data.worldHeight, 1e-9)
        assertEquals(10.0, data.fitStart, 1e-9)
        assertEquals(110.0, data.fitEnd, 1e-9)
        assertEquals(0.0, data.timeOffset, 0.0)
        assertEquals("main", data.threadName)
    }

    @Test
    fun threadModeMapsThreadTimes() {
        val rec = FakeProfileData("m", level = 0,
            globalStart = 10.0, globalEnd = 110.0, threadStart = 1.0, threadEnd = 50.0)
        val result = FakeResult(
            listOf(rec),
            threadTimeBounds = mapOf(THREAD_ID to bounds(1.0, 50.0)),
            globalTimeBounds = mapOf(THREAD_ID to bounds(10.0, 110.0)),
        )

        val data = RacingViewDataFactory.create(result, THREAD_ID, isThreadTime = true)

        assertEquals(1.0, data.rows[0].startMs, 1e-9)
        assertEquals(49.0, data.rows[0].durMs, 1e-9)
        assertEquals(50.0, data.worldWidth, 1e-9)
        assertEquals(1.0, data.fitStart, 1e-9)
        assertEquals(50.0, data.fitEnd, 1e-9)
    }

    @Test
    fun negativeThreadTimesAreShiftedIntoWorldAndCompensatedByTimeOffset() {
        val rec = FakeProfileData("m", level = 0,
            globalStart = 0.0, globalEnd = 1.0, threadStart = -10.0, threadEnd = 20.0)
        val result = FakeResult(
            listOf(rec),
            threadTimeBounds = mapOf(THREAD_ID to bounds(-10.0, 20.0)),
            globalTimeBounds = mapOf(THREAD_ID to bounds(0.0, 1.0)),
        )

        val data = RacingViewDataFactory.create(result, THREAD_ID, isThreadTime = true)

        assertEquals(10.0, data.timeOffset, 1e-9)
        assertEquals(0.0, data.rows[0].startMs, 1e-9)
        assertEquals(30.0, data.rows[0].durMs, 1e-9)
        assertEquals(0.0, data.fitStart, 1e-9)
        assertEquals(30.0, data.fitEnd, 1e-9)
        assertEquals(30.0, data.worldWidth, 1e-9)
    }

    @Test
    fun emptyThreadProducesValidNonZeroWorld() {
        val result = FakeResult(
            emptyList(),
            threadTimeBounds = emptyMap(),
            globalTimeBounds = mapOf(THREAD_ID to bounds(0.0, 100.0)),
        )

        val data = RacingViewDataFactory.create(result, THREAD_ID, isThreadTime = false)

        assertTrue(data.rows.isEmpty())
        assertTrue(data.worldWidth >= 1.0)
        assertTrue(data.worldHeight >= VirtualSpacePanel.ROW_H)
        assertEquals(0.0, data.fitStart, 0.0)
    }

    @Test
    fun unknownThreadFallsBackToEmptyRowsAndGeneratedName() {
        val result = FakeResult(
            listOf(FakeProfileData("m", 0, 0.0, 5.0, 0.0, 5.0)),
            threadTimeBounds = emptyMap(),
            globalTimeBounds = emptyMap(),
        )

        val data = RacingViewDataFactory.create(result, threadId = 4242, isThreadTime = false)

        assertTrue(data.rows.isEmpty())
        assertEquals("thread 4242", data.threadName)
    }

    private companion object {
        const val THREAD_ID = 1
    }
}
