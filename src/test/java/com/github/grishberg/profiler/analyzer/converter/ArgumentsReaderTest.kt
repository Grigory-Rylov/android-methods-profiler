package com.github.grishberg.profiler.analyzer.converter

import junit.framework.Assert.assertEquals
import org.junit.Test


internal class ArgumentsReaderTest {
    val underTest = ArgumentsReader()

    @Test
    fun `handle empty arguments`() {
        val result = underTest.read("")
        assertEquals(result.size, 0)
    }

    @Test
    fun `handle boolean`() {
        val result = underTest.read("Z")
        assertEquals(result.last().name, "boolean")
        assertEquals(result.last().type, Type.SIMPLE)
    }

    @Test
    fun `handle array of byte`() {
        val result = underTest.read("[B")
        assertEquals(result.last().name, "byte[]")
        assertEquals(result.last().type, Type.SIMPLE)
    }

    @Test
    fun `handle int boolean long double`() {
        val result = underTest.read("IZJD")

        val intElement = result[0]
        assertEquals(intElement.name, "int")
        assertEquals(intElement.type, Type.SIMPLE)

        val booleanElement = result[1]
        assertEquals(booleanElement.name, "boolean")
        assertEquals(booleanElement.type, Type.SIMPLE)

        val longElement = result[2]
        assertEquals(longElement.name, "long")
        assertEquals(longElement.type, Type.SIMPLE)

        val doubleElement = result[3]
        assertEquals(doubleElement.name, "double")
        assertEquals(doubleElement.type, Type.SIMPLE)
    }

    @Test
    fun `handle string`() {
        val result = underTest.read("Ljava/lang/String;")
        assertEquals("java.lang.String", result.last().name)
        assertEquals(Type.OBJECT, result.last().type)
    }

    @Test
    fun `handle obfuscated`() {
        val result = underTest.read("Laabd\$bbb;")
        assertEquals("aabd\$bbb", result.last().name)
        assertEquals(Type.OBFUSCATED, result.last().type)
    }

    @Test
    fun `handle int long obfuscated`() {
        val result = underTest.read("IJLaabd\$bbb;")

        val intElement = result[0]
        assertEquals("int", intElement.name)
        assertEquals(Type.SIMPLE, intElement.type)

        val longElement = result[1]
        assertEquals("long", longElement.name)
        assertEquals(Type.SIMPLE, longElement.type)

        val obfuscatedElement = result[2]
        assertEquals("aabd\$bbb", obfuscatedElement.name)
        assertEquals(Type.OBFUSCATED, obfuscatedElement.type)
    }

}