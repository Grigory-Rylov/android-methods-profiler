package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.core.AnalyzerResult
import java.io.File

class AllThreadsMethodsReportGenerator(
    private val data: AnalyzerResult,
) : ReportGenerator {

    override fun generate(
        file: File, onlyConstructor: Boolean, minimumDurationInMs: Int, packageFilter: String
    ) {
        val baseDir = file.path

        data.threads.forEach { threadItem ->
            val profileData = data.data[threadItem.threadId] ?: return
            val reportFile = File(baseDir, normalizeFileName(threadItem.name))
            val singleThreadReportGenerator = FlatMethodsReportGenerator(profileData)
            singleThreadReportGenerator.generate(
                reportFile, onlyConstructor, minimumDurationInMs, packageFilter
            )
        }
    }

    private fun normalizeFileName(threadName: String): String {
        return threadName.replace("/", "_").replace("\\", "_").replace(":", "_")
    }
}
