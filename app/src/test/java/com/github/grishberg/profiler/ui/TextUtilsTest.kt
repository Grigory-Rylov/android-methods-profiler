package com.github.grishberg.profiler.ui

import org.junit.Assert.assertEquals
import org.junit.Test


internal class TextUtilsTest {
    val underTest = TextUtils()

    @Test
    fun shortClassMethodName() {
        assertEquals("Foo.method", underTest.shortClassMethodName("com.test.Foo.method"))
    }

    @Test
    fun shortClassName() {
        assertEquals("Foo", underTest.shortClassName("com.test.Foo.method"))
    }
}