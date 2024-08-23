package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.common.TestLogger
import java.io.File
import junit.framework.TestCase
import junit.framework.TestCase.assertNotNull
import org.junit.Test

internal class TraceAnalyzerTest {

    private val underTest = TraceAnalyzer(TestLogger())

    @Test
    fun `test simplePerf trace parsing`() {
        val result = underTest.analyze(getSimplePerfFile())
        assertNotNull(result)

        TestCase.assertEquals(24, result.threads.size)

        val mainThreadData = result.data[result.mainThreadId]

        assertNotNull(mainThreadData)

        TestCase.assertEquals(38319, mainThreadData!!.size)
    }

    @Test
    fun `test legacy trace parsing`() {
        val result = underTest.analyze(getLegacyTraceFile())
        assertNotNull(result)
    }

    private fun getSimplePerfFile(): File {
        val classLoader = javaClass.classLoader
        val filePath = classLoader.getResource("simpleperf.trace")?.file ?: throw IllegalStateException()
        return File(filePath)
    }

    private fun getLegacyTraceFile(): File {
        val classLoader = javaClass.classLoader
        val filePath = classLoader.getResource("legacy.trace")?.file ?: throw IllegalStateException()
        return File(filePath)
    }
}
