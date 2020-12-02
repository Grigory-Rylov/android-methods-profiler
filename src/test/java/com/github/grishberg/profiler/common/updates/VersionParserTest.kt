package com.github.grishberg.profiler.common.updates

import org.junit.Assert
import org.junit.Test

class VersionParserTest {
    private val underTest = VersionParser("20.11.13.0")

    @Test
    fun checkNewVersion() {
        Assert.assertTrue(underTest.shouldUpdate("20.11.30.0"))
    }

    @Test
    fun checkSameVersion() {
        Assert.assertFalse(underTest.shouldUpdate("20.11.13.0"))
    }

    @Test
    fun checkOldVersion() {
        Assert.assertFalse(underTest.shouldUpdate("20.11.01.0"))
    }
}
