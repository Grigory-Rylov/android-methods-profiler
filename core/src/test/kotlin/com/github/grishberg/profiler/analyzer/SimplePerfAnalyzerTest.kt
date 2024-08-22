package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.analyzer.converter.NoOpNameConverter
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.TestLogger
import java.io.File
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test

internal class SimplePerfAnalyzerTest {

    private val logger: AppLogger = TestLogger()
    private val underTest = SimplePerfAnalyzer(logger, NoOpNameConverter)

    @Test
    fun `test simpleperf analyzer`() {
        val testFile = getSimplePerfFile()
        val result = underTest.analyze(testFile)

        assertEquals(24, result.threads.size)

        val mainThreadData = result.data[result.mainThreadId]

        assertNotNull(mainThreadData)

        assertEquals(38319, mainThreadData!!.size)

    }

    private fun getSimplePerfFile(): File {
        val classLoader = javaClass.classLoader
        val filePath = classLoader.getResource("simpleperf.trace")?.file ?: throw IllegalStateException()
        return File(filePath)
    }
}
