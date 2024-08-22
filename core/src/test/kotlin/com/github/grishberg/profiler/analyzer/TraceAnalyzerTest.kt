package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.common.TestLogger
import java.io.File
import org.junit.Test

internal class TraceAnalyzerTest {
    private val underTest = TraceAnalyzer(TestLogger())

    @Test
    fun `test simplePerf trace parsing`(){
        underTest.analyze(getSimplePerfFile())
    }

    private fun getSimplePerfFile(): File {
        val classLoader = javaClass.classLoader
        val filePath = classLoader.getResource("simpleperf.trace")?.file ?: throw IllegalStateException()
        return File(filePath)
    }

}
