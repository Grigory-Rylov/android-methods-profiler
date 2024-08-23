package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.common.TestLogger
import java.io.File
import junit.framework.TestCase.assertNotNull
import org.junit.Test

internal class TraceAnalyzerTest {

    private val underTest = TraceAnalyzer(TestLogger())

    @Test
    fun `test simplePerf trace parsing`() {
        val result = underTest.analyze(getSimplePerfFile())
        assertNotNull(result)
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
