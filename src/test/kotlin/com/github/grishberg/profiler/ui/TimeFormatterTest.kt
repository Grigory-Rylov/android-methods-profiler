package com.github.grishberg.profiler.ui

import org.junit.Assert
import org.junit.Test

internal class TimeFormatterTest {
    val underTest = TimeFormatter()

    @Test
    fun `format time to string`() {
        Assert.assertEquals("01:00.000", underTest.timeToString(60000.0))
        Assert.assertEquals("00:00.123", underTest.timeToString(123.456))
    }

    @Test
    fun `format microseconds to string`() {
        Assert.assertEquals("456 μs", underTest.timeToString(0.456))
    }

}