package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.core.ThreadTimeBounds
import com.github.grishberg.profiler.analyzer.ThreadItemImpl
import com.github.grishberg.profiler.child
import com.github.grishberg.profiler.fillWith
import com.github.grishberg.profiler.get
import com.github.grishberg.profiler.profileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceProfileDataSearchInfoTest {
    private val traceRootRef = profileData("A", 0.0, 100.0).apply {
        child("B", 0.0, 50.0).apply {
            child("C", 0.0, 25.0)
            child("C", 25.0, 50.0)
        }
        child("B", 50.0, 100.0).apply {
            child("C", 50.0, 75.0)
            child("C", 75.0, 100.0)
        }
    }

    private val traceRootToFindIn = profileData("A", 0.0, 90.0).apply {
        child("B", 0.0, 30.0)
            .child("C", 0.0, 30.0)
        child("B", 30.0, 60.0)
            .child("C", 30.0, 60.0)
        child("B", 60.0, 90.0)
            .child("C", 60.0, 90.0)
    }

    private val trace = object : AnalyzerResult {
        override val data = mutableMapOf(
            0 to mutableListOf<ProfileData>().apply { fillWith(traceRootToFindIn) }
        )
        override val globalTimeBounds: Map<Int, ThreadTimeBounds> = emptyMap()
        override val mainThreadId = 0
        override val maxLevel = Int.MAX_VALUE
        override val startTimeUs = 0L
        override val threadTimeBounds: Map<Int, ThreadTimeBounds> = emptyMap()
        override val threads = listOf(ThreadItemImpl("test", 0))
        override val minThreadTime: Double = 0.0
        override val minGlobalTime: Double = 0.0
    }

    private val underTest = TraceProfileDataFinder(trace)

    @Test
    fun `find to compare test`() {
        val dataToFind = traceRootRef.get("B").get("C")[2]
        val expected = traceRootToFindIn.get("B").get("C")[1]
        val found = underTest.findToCompare(dataToFind)

        assertEquals(1, found.size)
        assertTrue(expected === found.first().node)
    }
}
