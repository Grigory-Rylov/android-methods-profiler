package com.github.grishberg.profiler.analyzer

import com.github.grishberg.profiler.chart.BookmarksRectangle
import com.github.grishberg.profiler.chart.CallTracePanel
import com.github.grishberg.profiler.core.ProfileData
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class FlatMethodsReportGenerator(
    private val profileData: List<ProfileData>,
    private val markers: List<BookmarksRectangle> = emptyList(),
) : ReportGenerator {

    override fun generate(
        file: File,
        onlyConstructor: Boolean,
        minimumDurationInMs: Int,
        packageFilter: String
    ) {
        val fos = FileOutputStream(file)

        BufferedWriter(OutputStreamWriter(fos)).use { bw ->
            bw.write("name\tglobal time\tthread time\tglobal self time\tthread self time")
            bw.newLine()

            profileData.forEach {
                val threadDuration = it.threadEndTimeInMillisecond - it.threadStartTimeInMillisecond
                val globalDuration = it.globalEndTimeInMillisecond - it.globalStartTimeInMillisecond
                if (threadDuration > minimumDurationInMs && (!onlyConstructor || isConstructor(it.name)) && (packageFilter.isEmpty() || it.name.startsWith(
                        packageFilter
                    ))
                ) {
                    bw.write(
                        String.format(
                            "%s\t%.3f\t%.3f\t%.3f\t%.3f",
                            it.name,
                            globalDuration,
                            threadDuration,
                            it.globalSelfTime,
                            it.threadSelfTime
                        )
                    )
                    bw.newLine()
                }

                val markerRectangle = findMarkerForElement(it)
                if (markerRectangle != null) {
                    bw.write("<marker>: ${markerRectangle.name}")
                    bw.newLine()
                }
            }
        }
    }

    private fun findMarkerForElement(profileData: ProfileData): BookmarksRectangle? {
        for (marker in markers) {
            if (marker.isForElement(profileData)) {
                return marker
            }
        }
        return null
    }

    private fun isConstructor(name: String): Boolean {
        return name.endsWith(".<init>")
    }
}
