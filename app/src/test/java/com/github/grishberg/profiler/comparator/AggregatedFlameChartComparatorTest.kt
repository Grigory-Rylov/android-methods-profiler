package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.aggregatedData
import com.github.grishberg.profiler.child
import com.github.grishberg.profiler.comparator.model.AggregatedFlameProfileData
import com.github.grishberg.profiler.comparator.model.FlameMarkType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AggregatedFlameChartComparatorTest {

    private lateinit var reference: AggregatedFlameProfileData

    private lateinit var tested: AggregatedFlameProfileData

    private val underTest = AggregatedFlameChartComparator()

    @Before
    fun setUp() {
        reference = aggregatedData("test", 1.0).apply {
            child("A", 1.0).apply {
                child("B", 3.0)
                    .child("C", 3.0)
                child("D", 1.0).apply {
                    child("B", 3.6)
                        .child("C", 2.6)
                    child("E", 1.0)
                }
            }
        }
        tested = aggregatedData("test", 1.0).apply {
            child("A", 1.0).apply {
                child("D", 1.0)
                    .child("B", 5.0).apply {
                        child("C", 3.0)
                        child("E", 2.0)
                    }
                child("B", 1.5)
                    .child("C", 1.5)
            }
        }
    }

    @Test
    fun `compare test`() {
        underTest.compare(reference, tested)
        assertEquals(FlameMarkType.NONE, reference.mark)
        assertEquals(FlameMarkType.NONE, tested.mark)

        val aRef = reference.children.first()
        val aTest = tested.children.first()
        assertEquals(FlameMarkType.NONE, aRef.mark)
        assertEquals(FlameMarkType.NONE, aTest.mark)

        val abRef = aRef.children.first()
        val abTest = aTest.children[1]
        assertEquals(FlameMarkType.MAYBE_OLD, abRef.mark)
        assertEquals(FlameMarkType.NONE, abTest.mark)

        val abcRef = abRef.children.first()
        val abcTest = abTest.children.first()
        assertEquals(FlameMarkType.MAYBE_OLD, abcRef.mark)
        assertEquals(FlameMarkType.NONE, abcTest.mark)

        val adRef = aRef.children[1]
        val adTest = aTest.children.first()
        assertEquals(FlameMarkType.NONE, adRef.mark)
        assertEquals(FlameMarkType.NONE, adTest.mark)

        val adbRef = adRef.children.first()
        val adbTest = adTest.children.first()
        assertEquals(FlameMarkType.NONE, adbRef.mark)
        assertEquals(FlameMarkType.MAYBE_NEW, adbTest.mark)

        val adbcRef = adbRef.children.first()
        val adbcTest = adbTest.children.first()
        assertEquals(FlameMarkType.NONE, adbcRef.mark)
        assertEquals(FlameMarkType.NONE, adbcTest.mark)

        val adeRef = adRef.children[1]
        val adbeTest = adbTest.children[1]
        assertEquals(FlameMarkType.OLD_OLD, adeRef.mark)
        assertEquals(FlameMarkType.NEW_NEW, adbeTest.mark)
    }
}
