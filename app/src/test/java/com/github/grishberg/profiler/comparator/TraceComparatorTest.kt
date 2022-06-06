package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.child
import com.github.grishberg.profiler.comparator.model.MarkType
import com.github.grishberg.profiler.profileData
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceComparatorTest {

    private val reference = profileData("A", 0.0, 100.0).apply {
        child("B", 0.0, 20.0)
            .child("C", 10.0, 20.0)
        child("D", 20.0, 100.0).apply {
            child("B", 20.0, 30.0)
                .child("C", 22.0, 30.0)
            child("C", 30.0, 40.0)
            child("B", 50.0, 70.0)
                .child("C", 50.0, 60.0)
            child("B", 80.0, 100.0)
        }
    }

    private val tested = profileData("A", 0.0, 100.0).apply {
        child("D", 0.0, 80.0).apply {
            child("B", 0.0, 10.0)
                .child("C", 2.0, 10.0)
            child("B", 10.0, 20.0)
                .child("C", 12.0, 20.0)
            child("B", 30.0, 50.0)
                .child("C", 30.0, 40.0)
            child("B", 60.0, 80.0)
        }
        child("B", 80.0, 100.0).apply {
            child("E", 80.0, 90.0)
            child("C", 90.0, 100.0)
        }
    }

    private val underTest = TraceComparator(mock())

    @Test
    fun `compare test`() {
        val (aRef, aTest) = underTest.compare(reference, tested)
        assertEquals(MarkType.COMPARED, aRef.mark)
        assertEquals(MarkType.COMPARED, aTest.mark)

        val abRef = aRef.children.first()
        val abTest = aTest.children[1]
        assertEquals(MarkType.COMPARED, abRef.mark)
        assertEquals(MarkType.COMPARED, abTest.mark)

        val abeTest = abTest.children.first()
        assertEquals(MarkType.NEW, abeTest.mark)

        val abcRef = abRef.children.first()
        val abcTest = abTest.children[1]
        assertEquals(MarkType.COMPARED, abcRef.mark)
        assertEquals(MarkType.COMPARED, abcTest.mark)

        val adRef = aRef.children[1]
        val adTest = aTest.children.first()
        assertEquals(MarkType.CHANGE_ORDER, adRef.mark)
        assertEquals(MarkType.CHANGE_ORDER, adTest.mark)

        val adb1Ref = adRef.children.first()
        val adb1Test = adTest.children.first()
        assertEquals(MarkType.COMPARED, adb1Ref.mark)
        assertEquals(MarkType.COMPARED, adb1Test.mark)

        val adb1cRef = adb1Ref.children.first()
        val adb1cTest = adb1Test.children.first()
        assertEquals(MarkType.COMPARED, adb1cRef.mark)
        assertEquals(MarkType.COMPARED, adb1cTest.mark)

        val adcRef = adRef.children[1]
        assertEquals(MarkType.OLD, adcRef.mark)

        val adb2Ref = adRef.children[2]
        val adb2Test = adTest.children[1]
        assertEquals(MarkType.COMPARED, adb2Ref.mark)
        assertEquals(MarkType.COMPARED, adb2Test.mark)

        val adb2cRef = adb2Ref.children.first()
        val adb2cTest = adb2Test.children.first()
        assertEquals(MarkType.COMPARED, adb2cRef.mark)
        assertEquals(MarkType.COMPARED, adb2cTest.mark)

        val adb3Ref = adRef.children[3]
        val adb3Test = adTest.children[2]
        assertEquals(MarkType.COMPARED, adb3Ref.mark)
        assertEquals(MarkType.COMPARED, adb3Test.mark)

        val adb3cTest = adb3Test.children.first()
        assertEquals(MarkType.NEW, adb3cTest.mark)

        val adb4Test = adTest.children[3]
        assertEquals(MarkType.NEW, adb4Test.mark)
    }
}
